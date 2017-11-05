/**
 * Class to provide easy access to creating cyclical modulation graphs using
 * a buffer. The buffer has the same number of channels as each modulation
 * source/destination has values.
 * @TODO Values may be delayed one control cycle. How to solve this?
 */
Mordule[slot] : Dictionary {
    var destinations;
    var keyList;
    var m_buffer;
    var m_channels;
    var m_indices;
    var m_taps;
    var sources;

    *new {
        arg keys, channels = 1, buffer = nil;
        ^ super.new.init(keys, channels, buffer);
    }

    init {
        arg keys, channels, buffer;
        keyList = keys;
        m_channels = channels;
        m_buffer = buffer;
        m_indices = [];
        m_taps = [];
        sources = [];
        destinations = [];
        this.initIndicesDictionary;
    }

    /**
     * Allow slotted keys, a la myMordule[key].tap().
     */
    at {
        arg key;
        if (this.indexOf(key).isNil.not) {
            ^ MorduleNode.new(this, this.indexOf(key), key);
        };
    }

    /**
     * Allow usage of keys like properties, a la myMordule.key.tap().
     */
    doesNotUnderstand {
        arg key;
        ^ this.at(key);
    }

    bufChannels {
        ^ BufChannels.kr(this.buffer);
    }

    buffer {
        this.ensureBuffer();
        ^ m_buffer;
    }

    bufFrames {
        ^ BufFrames.kr(this.buffer);
    }

    /**
     * Get the number of channels in the buffer.
     */
    channels {
        ^ m_channels;
    }

    /**
     * Set a modulation key's current value to 0.
     * @param Symbol key
     *   The modulation key that should be zeroed out.
     */
    clear {
        arg key;
        this.at(key).clear();
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
        var k = this.at(key);
        k.insert(value, scale);
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
        index = this.indexSelect(n, keys);
        this.insertIndex(index, value);
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
    matrixReadInsert {
        arg inIndex, inKeys, outIndex, outKeys, scale = 1;
        var v;
        v = this.readSelect(inIndex, inKeys);
        this.insertSelect(outIndex, outKeys, v * scale);
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
        ^ this.at(key).read(clip, scale);
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
        ^ this.at(key).tap(clip, scale);
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
        ^ this.at(key).write(value);
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
        index = this.indexSelect(n, keys);
        ^ this.writeIndex(index, value);
    }

    clearIndex {
        arg index;
        ^ this.writeIndex(index, 0);
    }

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
     * buffer read or write.
     */
    ensureBuffer {
        if (m_buffer.isNil) {
            m_buffer = LocalBuf(keyList.size, m_channels).clear;
        };
    }

    hasDestination {
        arg key;
        ^ (destinations.size == 0 || { destinations.includes(key); });
    }

    hasSource {
        arg key;
        ^ (sources.size == 0 || { sources.includes(key); });
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
        var index = keyList.indexOf(key);
        if (index.isNil) {
            Exception('There is no key ' ++ key ++ '.').throw;
        };
        ^ index;
    }

    /**
     * Generate indices dictionary after each value is added.
     */
    initIndicesDictionary {
        var indices = Dictionary[];
        keyList.do {
            arg key, index;
            indices[key] = index;
        };
        m_indices = indices;
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

    /**
     * @see read, only uses a buffer index instead of a modulation key.
     */
    readIndex {
        arg index, clip = 1, scale = 1, insertValue = nil;
        var v;
        this.ensureBuffer();
        v = BufRd.kr(m_channels, m_buffer, index, 1, 0);
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
    warnDestinations {
        arg key;
        if (this.hasDestination.not) {
            warn(key ++ ' is not a listed destination.');
        };
    }

    /**
     * Trigger a warning if the key is not a listed source.
     */
    warnSources {
        arg key;
        if (this.hasSource.not) {
            warn(key ++ ' is not a listed source.');
        };
    }

    /**
     * @see write, only uses a buffer index instead of a modulation key.
     */
    writeIndex {
        arg index, value;
        this.ensureBuffer();
        if (value.size > m_channels) {
            warn('WARNING: adding more values than channels');
        };

        // value should be an array for wrapExtend to work.
        value = if (value.isKindOf(Collection), {value}, {[value]});
        value = wrapExtend(value, m_channels);

        BufWr.kr(value, this.buffer, index);
    }
}

/**
 * Syntactical sugar to allow for syntaxes like:
 * myMordule[mod].tap()
 * myMordule.mod.tap()
 * @see Mordule
 */
MorduleNode {

    var mordule;
    var index;
    var key;

    *new {
        arg m, i, k;
        ^ super.new.init(m, i, k);
    }

    init {
        arg m, i, k;
        mordule = m;
        index = i;
        key = k;
    }

    clear {
        ^ mordule.clearIndex(index);
    }

    insert {
        arg value, scale = 1;
        mordule.warnDestinations(key);
        ^ mordule.insertIndex(index, value, scale);
    }

    read {
        arg clip = 1, scale = 1;
        ^ mordule.readIndex(index, clip, scale);
    }

    tap {
        arg clip = 1, scale = 1;
        mordule.warnDestinations(key);
        mordule.registerTap(key);
        ^ mordule.tapIndex(index, clip, scale);
    }

    write {
        arg value;
        mordule.warnSources(key);
        ^ mordule.writeIndex(index, value);
    }
}
