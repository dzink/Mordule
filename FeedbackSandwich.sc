FeedbackSandwich : UGen {
	*ar {
		arg algo, channels = 1;
		var signal, buffer;

		buffer = LocalBuf(BlockSize.ir, channels).clear;
		signal = PlayBuf.ar(channels, buffer, loop: 1);

		// Perform an action on the signal.
		signal = algo.(signal);

		if (signal.size < channels) {
			signal.asArray.wrapExtend(channels);
		};

		RecordBuf.ar(signal, buffer, run: 1, loop: 1);
		^ signal;
	}
}
