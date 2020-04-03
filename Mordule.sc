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
  var <sourceBuffer;
  var <targetBuffer;
  var <channels;
  var <includeNilIndex;

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
    arg sources, targets, channels = 1, buffer = nil, includeNilIndex = true;
    ^ super.new.init(sources, targets, channels, buffer, includeNilIndex);
  }

  *kr {
    arg sources, targets, channels = 1, buffer = nil, includeNilIndex = true;
    ^ super.new.init(sources, targets, channels, buffer, includeNilIndex);
  }

  *ar {
    arg sources, targets, channels = 1, buffer = nil, includeNilIndex = true;
    ^ super.new.initAr(sources, targets, channels, buffer, includeNilIndex);
  }

  init {
    arg a_sources, a_targets, a_channels, a_buffer, a_includeNilIndex;

    // Don't worry about channels or nilIndex in constructors, they're re-set
    // immediately below.
    sourceBuffer = MorduleBuffer(a_sources);
    targetBuffer = MorduleBuffer(a_targets);
    this.includeNilIndex = a_includeNilIndex.asBoolean;
    this.channels = a_channels;
    ^ this;
  }

  initAr {
    arg a_sources, a_targets, a_channels, a_buffer, a_includeNilIndex;

    // Don't worry about channels or nilIndex in constructors, they're re-set
    // immediately below.
    sourceBuffer = MorduleBufferAr(a_sources, a_channels);
    targetBuffer = MorduleBufferAr(a_targets, a_channels);
    this.includeNilIndex = a_includeNilIndex.asBoolean;
    this.channels = a_channels;
    ^ this;
  }

  sources {
    ^ sourceBuffer;
  }

  targets {
    ^ targetBuffer;
  }

  sources_ {
    arg a_sources;
    sourceBuffer.keys = a_sources;
    ^ this;
  }

  targets_ {
    arg a_targets;
    targetBuffer.keys = a_targets;
    ^ this;
  }

  channels_ {
    arg a_channels;
    sourceBuffer.channels = a_channels;
    targetBuffer.channels = a_channels;
    ^ this;
  }

  includeNilIndex_ {
    arg a_include;
    sourceBuffer.nilIndex = a_include;
    targetBuffer.nilIndex = a_include;
    includeNilIndex = a_include;
    ^ this;
  }

  /**
   * Sets a source value.
   */
  writeSource {
    arg key, value;
    ^ sourceBuffer.writeKey(key, value);
  }

  /**
   * Gets a source value.
   */
  readSource {
    arg key, mul = 1, add = 0;
    ^ sourceBuffer.readKey(key, mul, add);
  }

  /**
   * Gets a target value.
   */
  readTarget {
    arg key, value, mul = 1, add = 0;
    ^ targetBuffer.readKey(key, value, mul, add);
  }

  /**
   * Tap the accumulated values of this target.
   * Resets the target back to zero.
   */
  tapTarget {
    arg key, mul = 1, add = 0;
    ^ targetBuffer.tapKey(key, mul, add);
  }

  tapTargetExponential {
    arg key, power;
    ^ pow(power, this.tapTarget(key));
  }

  /**
   * Accumulate values for this target.
   */
  insertTarget {
    arg key, value, mul = 1;
    targetBuffer.insertKey(key, value, mul);
    ^ this;
  }

  /**
   * Connect a named source to a named target.
   */
  connect {
    arg source, target, mul = 1, add = 0;
    this.insertTarget(target, this.readSource(source, mul, add));
    ^ this;
  }

  /**
   * Insert a value into a selectable target.
   * @param Array subkeys
   *   An array of keys which are available to be chosen between. All keys by
   *   default.
   */
  insertSelectTarget {
    arg index, value, subkeys = nil, mul = 1;
    var targetIndex = targetBuffer.selectIndex(index, subkeys);
    targetBuffer.insert(targetIndex, value * mul);
    ^ this;
  }

  readSelectSource {
    arg index, subkeys = nil, mul = 1, add = 0;
    var sourceIndex = sourceBuffer.selectIndex(index, subkeys);
    ^ sourceBuffer.read(sourceIndex, mul, add);
  }

  /**
   * Connect a named source to a selectable target.
   */
  connectSelectTarget {
    arg source, index, subkeys = nil, mul = 1;
    var value = this.readSource(source);
    this.insertSelectTarget(index, value, subkeys, mul);
    ^ this;
  }

  /**
   * Connect a selectable source to a named target.
   */
  connectSelectSource {
    arg sourceIndex, target, subkeys = nil, mul = 1, add = 0;
    var value = this.readSelectSource(sourceIndex, subkeys, mul, add);
    this.insertTarget(target, value, mul);
    ^ this;
  }

  /**
   * Connect a selectable source to a selectable target.
   */
  connectDoubleSelect {
    arg sourceIndex, targetIndex, sourceSubKeys = nil, targetSubkeys = nil, mul = 1, add = 0;
    var value = this.readSelectSource(sourceIndex, sourceSubKeys);
    this.insertSelectTarget(targetIndex, value.madd(mul, add), targetSubkeys)
    ^ this;
  }
}
