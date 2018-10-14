MorduleBuffer {
  var <buffer;
	var <keys;
	var <channels;
	var <nilIndex;

	*new {
		arg keys = [], channels = 1, nilIndex = true;
		var mb = super.new();
		mb.nilIndex = nilIndex;
		mb.channels = channels;
		mb.keys = keys;
		^ mb;
	}

	channels_ {
		arg num;
		if (this.testBufferLocked.not) {
			channels = num;
		} {
			"Can't change MorduleBuffer channel count after buffer is allocated".warn;
		};
		^ this;
	}

	keys_ {
		arg a_keys;
		keys = [];
    if (nilIndex) {
      // keys.add(\nil)
    };
    keys = keys.addAll(a_keys);
		^ this;
	}

	nilIndex_ {
		arg a_nilIndex;
		nilIndex = a_nilIndex.asBoolean();
		^ this;
	}

	ensureBuffer {
		if (this.testBufferLocked.not) {
			buffer = LocalBuf(keys.size, channels).clear;
		};
		^ this;
	}

	indexOf {
		arg key, force = true;
		var i = keys.indexOf(key);
		if (i == nil && force) {
			("MorduleBuffer key not found: " ++ key).error;
		};
		^ i;
	}

	read {
    arg index, mul = 1, add = 0;
    this.ensureBuffer();
		^ BufRd.kr(channels, buffer, index, interpolation: 1).madd(mul, add);
  }

	readKey {
		arg key, mul = 1, add =0;
		var index = this.indexOf(key);
		^ this.read(index, mul, add);
	}

	write {
    arg index, v;
    this.ensureBuffer();
		v = v.asArray.wrapExtend(channels);
		BufWr.kr(v, buffer, index);
		^ this;
  }

	writeKey {
		arg key, v;
		var index = this.indexOf(key);
		^ this.write(index, v);
	}

	insert {
		arg index, v;
		this.write(index, v + this.read(index));
		^ this;
	}

	insertKey {
		arg key, v;
		var index = this.indexOf(key);
		^ this.insert(index, v);
	}

	tap {
		arg index, mul = 1, add = 0;
		var v = this.read(index, mul, add);
		this.write(index, 0);
		^ v;
	}

	tapKey {
		arg key, mul = 1, add = 0;
		var index = this.indexOf(key);
		^ this.tap(index, mul, add);
	}

	selectIndex {
		arg index, subset = nil;
		var subkeys;
		subset = subset.defaultWhenNil(keys);
		subkeys = subset.collect {
			arg key;
			this.indexOf(key);
		};
		^ Select.kr(index, subkeys);
	}

	add {
		arg key;
		if (this.testBufferLocked.not) {
			keys.add(key);
		} {
			"Can't change MorduleBuffer keys after buffer is allocated".warn;
		};
		^ this;
	}

	remove {
		arg key;
		if (this.testBufferLocked.not) {
			keys.remove(key);
		} {
			"Can't change MorduleBuffer keys after buffer is allocated".warn;
		};
		^ this;
	}

	testBufferLocked {
		^ buffer.isNil.not;
	}
}
