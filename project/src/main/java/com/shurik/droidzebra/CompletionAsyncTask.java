package com.shurik.droidzebra;

import android.os.AsyncTask;


public class CompletionAsyncTask extends AsyncTask<Void, Void, Void> {
    private Runnable completion;

    private Runnable waiting;

    CompletionAsyncTask(final Runnable completion, ZebraEngine engine) {
        this(engine::waitForReadyToPlay, completion);
    }

    // Runs waiting in the background, then completion on the main thread.
    CompletionAsyncTask(Runnable waiting, Runnable completion) {
        this.waiting = waiting;
        this.completion = completion;
    }

    @Override
    protected Void doInBackground(Void... p) {
        waiting.run();
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        completion.run();
    }
}
