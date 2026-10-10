package de.earthlingz.oerszebra.analysis;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;

import com.shurik.droidzebra.Move;

import org.junit.Test;

import de.earthlingz.oerszebra.R;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;

public class AnalysisAdapterTest {

    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;

    // The zero line used to be white in every row, which left a white stub
    // next to the black bar of a Black-favoring score.
    @Test
    public void zeroLineTakesTheColorOfTheBar() {
        Context context = getInstrumentation().getTargetContext();
        View row = LayoutInflater.from(context).inflate(R.layout.analysis_eval_bar_item, null);
        AnalysisAdapter.ViewHolder holder = new AnalysisAdapter.ViewHolder(row);
        View centerLine = row.findViewById(R.id.eval_bar_center);
        Move move = new Move(2, 3);

        holder.bind(new MoveEval(1, move, -5 * 128), null);
        assertEquals("Black ahead", BLACK, colorOf(centerLine));

        // the same (recycled) row must switch back
        holder.bind(new MoveEval(2, move, 5 * 128), null);
        assertEquals("White ahead", WHITE, colorOf(centerLine));

        holder.bind(new MoveEval(3, move, 0), null);
        assertEquals("even", WHITE, colorOf(centerLine));

        holder.bind(MoveEval.pending(4, move), null);
        assertEquals("not evaluated yet", WHITE, colorOf(centerLine));
    }

    private static int colorOf(View view) {
        return ((ColorDrawable) view.getBackground()).getColor();
    }
}
