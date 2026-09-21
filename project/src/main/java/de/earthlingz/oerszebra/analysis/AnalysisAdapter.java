package de.earthlingz.oerszebra.analysis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
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
            double discs = eval.getDiscs();
            scoreLabel.setText(String.format(Locale.getDefault(), "%+.2f", discs));

            double fraction = Math.min(Math.abs(discs) / MAX_MAGNITUDE_DISCS, 1.0);
            float blackWeight = discs < 0 ? (float) fraction : 0f;
            float whiteWeight = discs > 0 ? (float) fraction : 0f;

            setWeight(blackSpacer, 1f - blackWeight);
            setWeight(blackBar, blackWeight);
            setWeight(whiteBar, whiteWeight);
            setWeight(whiteSpacer, 1f - whiteWeight);

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
