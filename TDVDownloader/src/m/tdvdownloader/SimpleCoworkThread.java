package m.tdvdownloader;

public abstract class SimpleCoworkThread<W> {
	private int threadCount;
	private int finished;
	private Throwable t;

	public SimpleCoworkThread(int threadCount) {
		this.threadCount = threadCount;
	}

	public synchronized void start() throws Throwable {
		finished = 0;
		for (int i = 0; i < threadCount; i++) {
			new Thread() {
				public void run() {
					try {
						while (true && t == null) {
							W work = null;
							synchronized(SimpleCoworkThread.this) {
								work = pickWork();
							}
							if (work == null) {
								break;
							} else {
								work(work);
							}
						}
					} catch (Throwable t) {
						SimpleCoworkThread.this.t = t;
					}

					synchronized(SimpleCoworkThread.this) {
						finished++;
						if (finished == threadCount) {
							SimpleCoworkThread.this.notify();
						}
					}
				}
			}.start();
		}
		wait();
		if (t != null) {
			StackTraceElement[] outTrace = Thread.currentThread().getStackTrace();
			StackTraceElement[] inTrace = t.getStackTrace();
			StackTraceElement[] allTrace = new StackTraceElement[outTrace.length + inTrace.length - 2];
			System.arraycopy(inTrace, 0, allTrace, 0, inTrace.length);
			System.arraycopy(outTrace, 2, allTrace, inTrace.length, outTrace.length - 2);
			t.setStackTrace(allTrace);
			throw t;
		}
	}

	protected abstract W pickWork() throws Throwable;

	protected abstract void work(W w) throws Throwable;

}
