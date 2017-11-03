class Mordule[slot] {
    var <keyList;
    var _buffer;
    var _indices;
    var _taps = [];
    var <sources = [];
    var <destinations = [];
    var <_channels;

    *new {
        arg keys, channels = 1, buffer = nil;
        _buffer = buffer;
        ^ super.new.init(keys, channels, buffer);
    }

    init {
        arg keys, channels, buffer;
        keyList = keys;
        _channels = channels;
        _buffer = buffer;
    }

    /**
     * Allow slotted keys, a la myMordule[key].tap().
     */
    at {
        arg key;
        if (this._indexOf(key).isNil.not) {
            ^ MorduleNode.new(this, this._indexOf(key));
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
        this._ensureBuffer();
        ^ BufChannels.kr(_buffer);
    }

    buffer {
        this._ensureBuffer();
        ^ _buffer;
    }

    bufFrames {
        this._ensureBuffer();
        ^ BufFrames.kr(_buffer);
    }

    bufNum {
        this._ensureBuffer();
        ^ BufNum.kr(_buffer);
    }

    /**
     * Get the number of channels in the buffer.
     */
    channels {
        ^ _channels;
    }

    /**
     * Set a modulation key's current value to 0.
     * @param Symbol key
     *   The modulation key that should be zeroed out.
     */
    clear {
        arg key;
        var index;
        index = this._indexOf(key);
        this._clearIndex(index);
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
        var index;
        index = this._indexOf(key);
        this._warnDestination(key);
        this._insertIndex(index, value, scale);
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
        index = this._indexSelect(n, keys);
        this._insertIndex(index, value);
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
        var index;
        index = this._indexOf(key);
        ^ this._readIndex(index, clip, scale);
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
        index = this._indexSelect(n, keys);
        ^ this._readIndex(index, clip, scale);
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
        arg key, value, clip = 1, scale = 1;
        var index;
        if (_taps.include(key)) {
            warn('The key ' ++ key ++ ' has already been tapped.');
        };
        _taps.add(key);
        this._warnDestination(key);
        index = this._indexOf(key);
        ^ this._writeIndex(index, value, clip, scale);
    }

    /**
     * Writes to a modulation source key.
     * @param Symbol key
     *   The key to read from.
     * @param float value
     *   The new value for the modulation source key.
     */
    write {
        arg key, value;
        var index;
        index = this._indexOf(key);
        this._warnSource(key);
        this._writeIndex(index, value);
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
        arg n, keys;
        var index;
        index = this._indexSelect(n, keys);
        ^ this._writeIndex(index, value);
    }

    _clearIndex {
        arg index;
        ^ this.write(index, 0);
    }

    _clipAndScale {
        arg value, clip = 1, scale = 1;
        if (clip.isNil.not) {
            v = clip(clip.neg, clip);
        };
        if (scale != 1 || { scale.isNil.not; }) {
            v = v * scale;
        }
        ^ v;
    }

    /**
     * If there is no buffer, create a local buf. This is checked before each
     * buffer read or write.
     */
    _ensureBuffer {
        if (_buffer.isNil) {
            _buffer = LocalBuf(keyList.size, _channels).clear;
        };
    }

    _hasDestination {
        arg key;
        ^ (destinations.size == 0 || { destinations.includes(key); });
    }

    _hasSource {
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
    _indexSelect {
        arg n, keys;
        var a, select;
        a = this._indicesOf(keys);

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
    _indicesOf {
        arg keys;
        indicesOf = keys.collect({
            arg key;
            this._indexOf(key);
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
    _indexOf {
        arg key;
        var index = keyList.indexOf(key);
        if (index.isNil) {
            warn('There is no key ' ++ key ++ '.');
        };
    }

    /**
     * Generate indices dictionary after each value is added.
     */
    _initIndicesDictionary {
        var indices = Dictionary[];
        keyList.do {
            arg key, index;
            indices[key] = index;
        }
        _indices = indices;
    }

    /**
     * @see insert, only uses a buffer index instead of a modulation key.
     */
    _insertIndex {
        arg index, value, scale = 1;
        var v = this._readIndex(index, nil, nil, value);
        ^ this._writeIndex(index, v * scale);
    }

    /**
     * @see read, only uses a buffer index instead of a modulation key.
     */
    _readIndex {
        arg index, clip = 1, scale = 1, insertValue = nil;
        var v;
        this._ensureBuffer();
        v = BufRd.kr(BufChannels.ir(buffer), buffer, index, n);
        if (insertValue.isNil.not) {
            v = v + insertValue;
        };
        ^ this._clipAndScale(v, clip, scale);
    }

    /**
     * @see tap, only uses a buffer index instead of a modulation key.
     */
    _tapIndex {
        arg index, clip = 1, scale = 1;
        var v = this._readIndex(index, clip, scale);
        this._clear(index);
        ^ v;
    }

    /**
     * Trigger a warning if the key is not a listed destination.
     */
    _warnDestinations {
        arg key;
        if (this._hasDestination.not) {
            warn(key ++ ' is not a listed destination.');
        };
    }

    /**
     * Trigger a warning if the key is not a listed source.
     */
    _warnSources {
        arg key;
        if (this._hasSource.not) {
            warn(key ++ ' is not a listed source.');
        };
    }

    /**
     * @see write, only uses a buffer index instead of a modulation key.
     */
    _writeIndex {
        arg index, n;
        this._ensureBuffer();
        ^ BufWrite.kr(BufChannels.ir(buffer), buffer, index, n);
    }
}

/**
 * Syntactical sugar to allow for syntaxes like:
 * myMordule[mod].tap()
 * myMordule.mod.tap()
 * @see Mordule
 */
class MorduleNode {

    var mordule;
    var key;

    *new {
        arg m, k;
        ^ super.new.init(m, k);
    }

    init {
        arg m, k;
        mordule = m;
        key = k;
    }

    clear {
        ^ mordule.clear(key);
    }

    insert {
        arg value;
        mordule._warnDestinations(key);
        ^ mordule.insert(key, value);
    }

    read {
        arg clip = 1, scale = 1;
        ^ mordule.read(key, clip, scale);
    }

    tap {
        arg clip = 1, scale = 1;
        mordule._warnDestinations(key);
        ^ mordule.tap(key, clip, scale);
    }

    write {
        arg value;
        mordule._warnSources(key);
        ^ mordule.write(key, value);
    }
}
