package de.earthlingz.oerszebra.analysis;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.earthlingz.oerszebra.R;

/**
 * Renders {@link MoveEval} results as a list of signed horizontal bars -
 * black growing left for a Black-favoring score, white growing right for a
 * White-favoring score - with the ply number and the numeric evaluation.
 */
public class AnalysisAdapter extends RecyclerView.Adapter<AnalysisAdapter.ViewHolder> {

    private static final double MAX_MAGNITUDE_DISCS = 64.0;

    public interface OnMoveSelectedListener {
        void onMoveSelected(MoveEval moveEval);
    }

    private final List<MoveEval> items = new ArrayList<>();
    private final OnMoveSelectedListener listener;

    public AnalysisAdapter(OnMoveSelectedListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MoveEval> newItems) {
        // The common case while analysis runs (in-flight-row updates for
        // the ply being analyzed, and that ply's own finalization) only
        // ever changes the *last* item - DroidZebra always rebuilds the
        // list as "the same resultsSoFar prefix" + one trailing row, so
        // the earlier entries are the literal same MoveEval instances each
        // time. Detecting that and updating just that one row is far
        // cheaper than a full notifyDataSetChanged() (which rebinds and
        // re-lays-out every attached row), and the saving matters more the
        // longer the list has grown - a full-game analysis has up to 60
        // rows by the time the earliest plies are reached.
        if (items.size() == newItems.size() && !items.isEmpty() && sameExceptLast(newItems)) {
            int lastIndex = items.size() - 1;
            items.set(lastIndex, newItems.get(lastIndex));
            notifyItemChanged(lastIndex);
            return;
        }
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    private boolean sameExceptLast(List<MoveEval> newItems) {
        for (int i = 0; i < items.size() - 1; i++) {
            if (items.get(i) != newItems.get(i)) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.analysis_eval_bar_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MoveEval eval = items.get(position);
        holder.bind(eval, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView plyLabel;
        private final TextView scoreLabel;
        private final View blackSpacer;
        private final View blackBar;
        private final View whiteBar;
        private final View whiteSpacer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            plyLabel = itemView.findViewById(R.id.eval_ply_label);
            scoreLabel = itemView.findViewById(R.id.eval_score_label);
            blackSpacer = itemView.findViewById(R.id.eval_bar_black_spacer);
            blackBar = itemView.findViewById(R.id.eval_bar_black);
            whiteBar = itemView.findViewById(R.id.eval_bar_white);
            whiteSpacer = itemView.findViewById(R.id.eval_bar_white_spacer);
        }

        void bind(MoveEval eval, OnMoveSelectedListener listener) {
            plyLabel.setText(String.valueOf(eval.getPly()));

            if (eval.isPending()) {
                // Score not known yet - no bar to draw, just a placeholder so
                // the row this ply will end up in is visible right away
                // instead of only appearing once the result lands.
                scoreLabel.setText(R.string.analysis_pending_score);
                setWeight(blackSpacer, 1f);
                setWeight(blackBar, 0f);
                setWeight(whiteBar, 0f);
                setWeight(whiteSpacer, 1f);
            } else {
                double discs = eval.getDiscs();
                scoreLabel.setText(String.format(Locale.getDefault(), "%+.2f", discs));

                double fraction = Math.min(Math.abs(discs) / MAX_MAGNITUDE_DISCS, 1.0);
                float blackWeight = discs < 0 ? (float) fraction : 0f;
                float whiteWeight = discs > 0 ? (float) fraction : 0f;

                setWeight(blackSpacer, 1f - blackWeight);
                setWeight(blackBar, blackWeight);
                setWeight(whiteBar, whiteWeight);
                setWeight(whiteSpacer, 1f - whiteWeight);
            }

            // Make the ply currently being analyzed stand out from already-
            // finished rows - isInProgress() (not isPending()) covers both
            // the initial placeholder and every interim update afterward,
            // so this stays lit for the whole ply instead of turning off
            // the moment the first real score arrives. Views get recycled,
            // so both branches must be set explicitly rather than only
            // applying the highlight.
            if (eval.isInProgress()) {
                itemView.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.analysis_in_progress_highlight));
                plyLabel.setTypeface(Typeface.DEFAULT_BOLD);
                scoreLabel.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
                // not setTypeface(getTypeface(), NORMAL): with style NORMAL
                // that just keeps the already-bold typeface
                plyLabel.setTypeface(Typeface.DEFAULT);
                scoreLabel.setTypeface(Typeface.DEFAULT);
            }

            itemView.setContentDescription(eval.getMove().getText());
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoveSelected(eval);
                }
            });
        }

        private static void setWeight(View view, float weight) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.weight = weight;
            view.setLayoutParams(params);
        }
    }
}
