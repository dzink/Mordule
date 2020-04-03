MorduleBufferAr : MorduleBuffer {
	var phasor;
	var blockSize;
	var zero;

	blockSize {
		if (blockSize.isNil) {
			blockSize = SampleRate.ir / ControlRate.ir;
		};
		^ blockSize;
	}

	phasor {
		if (phasor.isNil) {
			phasor = Phasor.ar(0, this.blockSize, 0, this.blockSize * keys.size);
		};
		^ phasor
	}

	zero {
		if (zero.isNil) {
			zero = DC.ar(0);
		};
		^ zero;
	}

	ensureBuffer {
		if (this.testBufferLocked.not) {
			buffer = LocalBuf(keys.size * this.blockSize(), channels).clear;
		};
		^ this;
	}

	read {
		arg index, mul = 1, add = 0;
		this.ensureBuffer();
		^ BufRd.ar(channels, buffer, index + this.phasor(), interpolation: 1).madd(mul, add);
	}

	write {
		arg index, v;
		this.ensureBuffer();
		v = v.asArray.wrapExtend(channels);
		[\v, v].postln;
		BufWr.ar(v, buffer, index + this.phasor());
		^ this;
	}

	tap {
		arg index, mul = 1, add = 0;
		var v = this.read(index, mul, add);
		this.write(index, this.zero);
		^ v;
	}

}
