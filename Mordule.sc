/**
 * Virtual analog modulation model.
 * Mordule represents a buffer and a list of symbols which correlate to indices
 * of that buffer, along with helper methods to model classic modulations,
 * including modulation matrices. Is also able to create circular modulation
 * graphs.
 *
 * @TODO Values may be delayed one control cycle. How to solve this? Is this something I should even worry about?
 * @TODO Make this work audio-rate
 * @TODO Make some of these methods private.
 */
Mordule {
    var buffer;
    var <channels;
    var <destinations;
    var <keyList;
    var <>includeNilIndex;
    var <sources;

    var m_taps;

    /**
     * Symbols in sources and destinations that overlap will be added as both
     * sources and destinations.
     * @param Collection sources
     *   A collection of Symbols that will be registered as sources.
     * @param Collection destinations
     *   A collection of Symbols that will be registered as destinations.
     * @param integer channels
     *   The number of channels to create in the buffer. Useful for stereo
     *   LFO's, etc. This number is universal across the whole Mordule instance.
     * @param Buffer buffer
     *   Explicitly pass in a Buffer. An error will be thrown if it's not the *   same size as the number of keys (@TODO)! @TODO also test to make sure
     *   it's a buffer or nil.
     * @param Boolean includeNilIndex
     *   Whether to include a location in the buffer that does not act as a
     *   modulator. Useful for selectInsert, selectWrite, etc methods, when one *   of the options should do nothing.
     * @return Mordule
     *   The new Mordule instance.
     */
    *new {
        arg sources, destinations, channels = 1, buffer = nil, includeNilIndex = true;
        ^ super.new.init(sources, destinations, channels, buffer, includeNilIndex);
    }

    /**
     * Handle the scenario when the user doesn't care about the difference
     * between sources and destinations. @see Mordule.new()
     */
    *newMixed {
        arg keys, channels = 1, buffer = nil, includeNilIndex = true;
        ^ super.new.init(keys, keys, channels, buffer, includeNilIndex);
    }

    init {
        arg a_sources, a_destinations, a_channels, a_buffer, a_includeNilIndex;
        includeNilIndex = a_includeNilIndex.asBoolean;
        buffer = a_buffer;
        m_taps = [];
        keyList = [];
        destinations = [];
        sources = [];
        this.channels = a_channels;
        this.addSources(a_sources);
        this.addDestinations(a_destinations);
    }

    /**
     * Getter for the buffer property.
     */
    buffer {
        this.ensureBuffer();
        ^ buffer;
    }

    /**
     * Setter for the buffer property.
     */
    buffer_ {
        arg a_buffer;
        if (a_buffer.isNil.not) {
            buffer = a_buffer;
            this.ensureBuffer();
        } {
            buffer = nil;
        };
    }

    /**
     * Setter for channels.
     */
    channels_ {
        arg a_channels;
        if (a_channels != channels && {
            (buffer.isKindOf(Buffer) || {
                buffer.isKindOf(LocalBuf)
            })
        }) {
            Exception('Buffer already allocated. Cannot alter the number of channels.').throw;
        };
        channels = a_channels;
    }

    addDestinations {
        arg ... keys;
        keys = if (keys.isKindOf(Collection)) {keys} {[keys]};
        keys.deepCollect(inf, {
            arg key;
            destinations = if (this.isNilKey(key).not) {destinations.add(key)} {destinations};
        });
        destinations = destinations.as(Set).as(Array);
        this.addKeys(destinations);
    }

    addKeys {
        arg ... keys;
        keys = if (keys.isKindOf(Collection)) {keys} {[keys]};
        keys.deepCollect(inf, {
            arg key;
            keyList = if (this.isNilKey(key).not) {keyList.add(key)} {keyList};
        });
        keyList = keyList.as(Set).as(Array);
    }

    addSources {
        arg ... keys;
        keys = if (keys.isKindOf(Collection)) {keys} {[keys]};
        keys.deepCollect(inf, {
            arg key;
            sources = if (this.isNilKey(key).not) {sources.add(key)} {sources};
        });
        sources = sources.as(Set).as(Array);
        this.addKeys(sources);
    }

    /**
     * @return UGen
     *   A modulatable number of channels.
     * @TODO kr vs ir?
     */
    bufChannels {
        ^ BufChannels.kr(this.buffer);
    }

    /**
     * @return UGen
     *   A modulatable number of frames.
     * @TODO kr vs ir?
     */
    bufFrames {
        ^ BufFrames.kr(this.buffer);
    }

    /**
     * Set a modulation key's current value to 0.
     * @param Symbol key
     *   The modulation key that should be zeroed out.
     */
    clear {
        arg key;
        this.clearIndex(this.indexOf(key));
    }

    /**
     * Add to a modulation key's running total.
     * @param Symbol key
     *   The modulation key that should be added to.
     * @param float value
     *   The amount to add.
     * @param float scale
     *   The amount to scale the added value by.
     */
    insert {
        arg key, value, scale = 1;
        this.enforceDestinations(key);
        this.insertIndex(this.indexOf(key), value, scale);
    }


    /**
     * Reads one modulation source and inserts it directly into a destination.
     */
    insertFromSource {
        arg sourceKey, destinationKey, scale = 1;
        var source;

        // No need to enforceDestinations, that is done via insert.
        this.enforceSources(sourceKey);
        source = this.read(sourceKey);
        this.insert(destinationKey, source, scale);
    }

    /**
     * Read the value at one modulation key, and add it to another modulation
     * key. Useful for flexible modulation matrices, such as in an MS2000.
     * @param UGen inIndex
     *   A modulatable index suitable for use in Select.kr which will select
     *   the source modulation key.
     * @param Array inKeys
     *   An array of source modulation key Symbols.
     * @param UGen outIndex
     *   A modulatable index suitable for use in Select.kr which will select
     *   the destination modulation key.
     * @param Array outKeys
     *   An array of destination modulation key Symbols.
     * @param float scale;
     *   A modifier to scale the amount to add to the destination.
     */
    insertFromSourceMatrix {
        arg sourceIndex, sourceKeys, destinationIndex, destinationKeys, scale = 1;
        var v;

        // Destinations are enforced in insertSelect.
        this.enforceSources(sourceKeys);

        v = this.readSelect(sourceIndex, sourceKeys);
        this.insertSelect(destinationIndex, destinationKeys, v * scale);
    }

    /**
     * Like @see insertSelect, only scales indices appropriately from -1..1 to
     * the number of keys.
     */
    insertFromSourceMatrixRange {
        arg sourceIndex, sourceKeys, destinationIndex, destinationKeys, scale = 1;
        sourceIndex = sourceIndex.range(0, sourceKeys.size).floor;
        destinationIndex = destinationIndex.range(0, destinationKeys.size).floor;
        this.insertFromSourceMatrix(sourceIndex, sourceKeys, destinationIndex, destinationKeys, scale);
    }


    /**
     * Add to the running total of one of list of modulation keys. When n is 0,
     * it will increase the first modulation key in the list; when n is 1, it
     * will increase the 2nd; etc etc.
     * @param Symbol key
     *   The index of the modulation key that should be added to.
     * @param Array keys
     *   The list of modulation key Symbols.
     * @param float value
     *   The amount to add to the key. Negative values will decrement.
     * @param float scale
     *   The amount to scale the added value by.
     */
    insertSelect {
        arg n, keys, value, scale = 1;
        var index;
        this.enforceDestinations(keys);
        index = this.indexSelect(n, keys);
        this.insertIndex(index, value);
    }

    /**
     * Like @see insertSelect, only scales n appropriately from -1..1 to the
     * number of keys.
     */
    insertSelectRange {
        arg n, keys, value, scale = 1;
        n = n.range(0, keys.size + 1).floor;
        this.insertSelect(n, keys, value, scale);
    }

    /**
     * Reads from a modulation destination or source key.
     * @param Symbol key
     *   The key to read from.
     * @param float clip
     *   The number from [clip.neg, clip] to clamp values between. Set to nil
     *   to not clip.
     * @param float scale
     *   The amount to scale the returned value by. Set to nil or 1 to not
     *   scale.
     * @return UGen
     *   The value of a modulation source key.
     */
    read {
        arg key, clip = 1, scale = 1;
        ^ this.readIndex(this.indexOf(key), clip, scale);
    }

    /**
     * Read a single value from one of list of modulation keys. When n is 0, it
     * will read the first modulation key in the list; when n is 1, it will
     * read the 2nd; etc etc.
     * @param Symbol key
     *   The index of the modulation key that should be added to.
     * @param Array keys
     *   The list of modulation key Symbols.
     * @param float clip
     *   The number from [clip.neg, clip] to clamp values between. Set to nil
     *   to not clip.
     * @param float scale
     *   The amount to scale the returned value by. Set to nil or 1 to not
     *   scale.
     * @return UGen
     *   The value of a modulation source key.
     */
    readSelect {
        arg n, keys, clip = 1, scale = 1;
        var index;
        index = this.indexSelect(n, keys);
        ^ this.readIndex(index, clip, scale);
    }
    /**
     * Like @see readSelect, only scales n appropriately from -1..1 to the
     * number of keys.
     */
    readSelectRange {
        arg n, keys, clip = 1, scale = 1;
        n = n.range(0, keys.size).floor;
        ^ this.readSelect(n, keys, clip, scale);
    }

    /**
     * Reads from a modulation destination key then clears the value.
     * @param Symbol key
     *   The key to read from.
     * @param float clip
     *   The number from [clip.neg, clip] to clamp values between. Set to nil
     *   to not clip.
     * @param float scale
     *   The amount to scale the returned value by. Set to nil or 1 to not
     *   scale.
     * @return UGen
     *   The value of a modulation source key.
     */
    tap {
        arg key, clip = 1, scale = 1;
        this.enforceDestinations(key);
        this.registerTap(key);
        ^ this.tapIndex(this.indexOf(key), clip, scale);
    }

    /**
     * Writes to a modulation source key.
     * @param Symbol key
     *   The key to read from.
     * @param float value
     *   The new value for the modulation source key. If this is not the same
     *   size as expected, it will wrapExtend as needed.
     */
    write {
        arg key, value;
        this.enforceSources(key);
        ^ this.writeIndex(this.indexOf(key), value);
    }

    /**
     * Write to a modulatable index. The intended function here is to be able
     * to select from a list of possible destinations and write.
     *
     * @param integer n
     *   The index to write to.
     * @param array keys
     *   The list of possible destinations. The indices here are
     */
    writeSelect {
        arg n, keys, value;
        var index;
        this.enforceSources(keys);
        index = this.indexSelect(n, keys);
        ^ this.writeIndex(index, value);
    }

    /**
     * Like @see writeSelect, only scales n appropriately from -1..1 to the
     * number of keys.
     */
     writeSelectRange {
         arg n, keys, value;
         n = n.range(0, keys.size).floor;
         ^ this.writeSelect(n, keys, value);
     }

    /**
     * Writes 0 to an index.
     */
    clearIndex {
        arg index;
        this.writeIndex(index, 0);
    }

    // This saves a few cycles by determining if scaling/clipping is needed.
    clipAndScale {
        arg value, clip = 1, scale = 1;
        if (clip.isNil.not) {
            value = value.clip(clip.neg, clip);
        };
        if (scale != 1 && { scale.isNil.not; }) {
            value = value * scale;
        };
        ^ value;
    }

    /**
     * If there is no buffer, create a local buf. This is checked before each
     * buffer read or write. Also make sure the buffer is of appropriate class
     * to be passed to a BufRd/BufWr UGen.
     */
    ensureBuffer {
        if (buffer.isNil) {
            // Add 1 to account for nil index.
            buffer = LocalBuf(keyList.size + includeNilIndex.asInteger, channels).clear;
        };
        if ((buffer.isKindOf(Buffer) ||  {
                buffer.isKindOf(UGen) || {
                    buffer.isKindOf(LocalBuf)
                }
            }).not {
            Exception('Specified buffer must be of type Buffer, UGen, or LocalBuf.').throw;
        });
    }

    /**
     * Returns whether an array of sources are all in the destination array.
     * If the array is empty, always return true. @TODO ???
     * Also counts keys that count as nil as being included implicitly if this
     * Mordule is of the @see includeNilIndex variety. @see isNilKey.
     */
    hasDestination {
        arg keys;
        if (destinations.size == 0) {
            ^ true;
        };
        keys = keys.asArray;
        keys.do {
            arg key;
            if (destinations.includes(key).not && {this.isNilKey(key).not}) {
                ^ key;
            };
        };
        ^ true;
    }

    /**
     * Returns whether an array of sources are all in the source array.
     * If the array is empty, always return true. @TODO ???
     * Also counts keys that count as nil as being included implicitly if this
     * Mordule is of the @see includeNilIndex variety. @see isNilKey.
     */
    hasSource {
        arg keys;
        if (sources.size == 0) {
            ^ true;
        };
        keys = keys.asArray;
        keys.do {
            arg key;
            if (sources.includes(key).not && {this.isNilKey(key).not}) {
                ^ key;
            };
        };
        ^ true;
    }

    /**
     * Select a buffer index based on a list of keys.
     *
     * @param integer n
     *   The modulatable index to a key from which to get the buffer index.
     * @param array keys
     *   An array of keys that represent different buffer indices.
     * @return UGen
     *   A buffer index that represents the selected key.
     */
    indexSelect {
        arg n, keys;
        var a, select;
        a = this.indicesOf(keys);
        select = Select.kr(n, a);
        ^ select;
    }

    /**
     * Get the buffer indices of an array of keys.
     * @param Array keys
     *   The array of keys to get buffer indices for.
     * @return Array
     *   An array of buffer indices that represents the given key Array.
     */
    indicesOf {
        arg keys;
        var indicesOf = keys.collect({
            arg key;
            this.indexOf(key);
        });
        ^ indicesOf;
    }

    /**
     * Get the buffer indices of a single keys.
     * @param Array key
     *   The key to get a buffer index for.
     * @return integer
     *   The buffer index that represents the given key.
     */
    indexOf {
        arg key;
        var index;
        if (includeNilIndex && {this.isNilKey(key)}) {
            ^ 0;
        };
        index = keyList.indexOf(key);
        if (index.isNil) {
            Exception('There is no key ' ++ key ++ '.').throw;
        };

        // Add 1 because 0 is the nil index.
        ^ index + includeNilIndex.asInteger;
    }

    /**
     * @see insert, only uses a buffer index instead of a modulation key.
     */
    insertIndex {
        arg index, value, scale = 1;
        var v;
        v = this.readIndex(index, nil, nil, value * scale);
        this.writeIndex(index, v);
    }

    readBuffer {
        arg index;
        ^ BufRd.kr(channels, buffer, index, 1, 0);
    }

    /**
     * @see read, only uses a buffer index instead of a modulation key.
     */
    readIndex {
        arg index, clip = 1, scale = 1, insertValue = nil;
        var v;
        this.ensureBuffer();
        v = this.readBuffer(index);
        if (includeNilIndex) {
            v = Select.kr(BinaryOpUGen('==', 0, index), [v, 0]);
        };
        if (insertValue.isNil.not) {
            v = v + insertValue;
        };
        ^ this.clipAndScale(v, clip, scale);
    }

    /**
     * A running list of tapped destinations. This will trigger a
     * warning if a destination is tapped more than once.
     */
    registerTap {
        arg key;
        if (m_taps.includes(key)) {
            warn('The key ' ++ key ++ ' has already been tapped.');
        };
        m_taps.add(key);
    }

    /**
     * @see tap, only uses a buffer index instead of a modulation key.
     */
    tapIndex {
        arg index, clip = 1, scale = 1;
        var v;
        v = this.readIndex(index, clip, scale);
        this.clearIndex(index);
        ^ v;
    }

    /**
     * Trigger a warning if the key is not a listed destination.
     */
    enforceDestinations {
        arg key;
        var result = this.hasDestination(key);
        if (result.isKindOf(Symbol)) {
            Exception(result ++ ' is not a listed destination.').throw;
        };
    }

    /**
     * Trigger a warning if the key is not a listed source.
     */
    enforceSources {
        arg key;
        var result = this.hasSource(key);
        if (result.isKindOf(Symbol)) {
            Exception(result ++ ' is not a listed source.').throw;
        };
    }

    /**
     * @see write, only uses a buffer index instead of a modulation key.
     */
    writeIndex {
        arg index, value;
        this.ensureBuffer();
        if (value.size > channels) {
            warn('WARNING: adding more values than channels');
        };
        if (includeNilIndex) {
            value = Select.kr(BinaryOpUGen('==', index, 0), [value, 0]);
        };

        // value should be an array for wrapExtend to work.
        value = if (value.isKindOf(Collection), {value}, {[value]});
        value = wrapExtend(value, channels);

        BufWr.kr(value, this.buffer, index);
    }

    /**
     * Determine if a mixed value should be interpreted as the nil index (the
     * nil index is in every Mordule buffer, and is used to sink values that
     * should not be modulated; this is useful for selectInsert, selectWrite,
     * etc methods, when one of the options should be to do nothing). There are
     * a few ways to indicate the nil index: 0, false, nil, and \nil are all
     * acceptable.
     * @param mixed key
     *   The key to test.
     * @return Boolean
     *   true if the key should be seen as nil.
     */
    isNilKey {
        arg key;
        ^ (includeNilIndex.asBoolean && (key.isNil || {
            key.asSymbol == \nil ||
                { key === false || {
                    key === 0
                }
            }
        }));
    }
}
