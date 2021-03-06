/****************************************************************************************
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;

import com.ichi2.libanki.Deck;
import com.ichi2.libanki.sched.DeckDueTreeNode;

import java.util.ArrayList;
import java.util.List;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.ViewHolder> {

    /* Make the selected deck roughly half transparent if there is a background */
    public static final double SELECTED_DECK_ALPHA_AGAINST_BACKGROUND = 0.45;

    private LayoutInflater mLayoutInflater;
    private List<DeckDueTreeNode> mDeckList;
    private int mZeroCountColor;
    private int mNewCountColor;
    private int mLearnCountColor;
    private int mReviewCountColor;
    private int mRowCurrentDrawable;
    private int mDeckNameDefaultColor;
    private int mDeckNameDynColor;
    private Drawable mExpandImage;
    private Drawable mCollapseImage;
    private Drawable mNoExpander = new ColorDrawable(Color.TRANSPARENT);

    // Listeners
    private View.OnClickListener mDeckClickListener;
    private View.OnClickListener mDeckExpanderClickListener;
    private View.OnLongClickListener mDeckLongClickListener;
    private View.OnClickListener mCountsClickListener;

    private Collection mCol;

    // Totals accumulated as each deck is processed
    private int mNew;
    private int mLrn;
    private int mRev;

    // Flags
    private boolean mHasSubdecks;

    // Whether we have a background (so some items should be partially transparent).
    private boolean mPartiallyTransparentForBackground;

    // ViewHolder class to save inflated views for recycling
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public RelativeLayout deckLayout;
        public LinearLayout countsLayout;
        public ImageButton deckExpander;
        public ImageButton indentView;
        public TextView deckName;
        public TextView deckNew, deckLearn, deckRev;

        public ViewHolder(View v) {
            super(v);
            deckLayout = (RelativeLayout) v.findViewById(R.id.DeckPickerHoriz);
            countsLayout = (LinearLayout) v.findViewById(R.id.counts_layout);
            deckExpander = (ImageButton) v.findViewById(R.id.deckpicker_expander);
            indentView = (ImageButton) v.findViewById(R.id.deckpicker_indent);
            deckName = (TextView) v.findViewById(R.id.deckpicker_name);
            deckNew = (TextView) v.findViewById(R.id.deckpicker_new);
            deckLearn = (TextView) v.findViewById(R.id.deckpicker_lrn);
            deckRev = (TextView) v.findViewById(R.id.deckpicker_rev);
        }
    }

    public DeckAdapter(LayoutInflater layoutInflater, Context context) {
        mLayoutInflater = layoutInflater;
        mDeckList = new ArrayList<>();
        // Get the colors from the theme attributes
        int[] attrs = new int[] {
                R.attr.zeroCountColor,
                R.attr.newCountColor,
                R.attr.learnCountColor,
                R.attr.reviewCountColor,
                R.attr.currentDeckBackground,
                android.R.attr.textColor,
                R.attr.dynDeckColor,
                R.attr.expandRef,
                R.attr.collapseRef };
        TypedArray ta = context.obtainStyledAttributes(attrs);
        mZeroCountColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black));
        mNewCountColor = ta.getColor(1, ContextCompat.getColor(context, R.color.black));
        mLearnCountColor = ta.getColor(2, ContextCompat.getColor(context, R.color.black));
        mReviewCountColor = ta.getColor(3, ContextCompat.getColor(context, R.color.black));
        mRowCurrentDrawable = ta.getResourceId(4, 0);
        mDeckNameDefaultColor = ta.getColor(5, ContextCompat.getColor(context, R.color.black));
        mDeckNameDynColor = ta.getColor(6, ContextCompat.getColor(context, R.color.material_blue_A700));
        mExpandImage = ta.getDrawable(7);
        mCollapseImage = ta.getDrawable(8);
        ta.recycle();
    }

    public void setDeckClickListener(View.OnClickListener listener) {
        mDeckClickListener = listener;
    }

    public void setCountsClickListener(View.OnClickListener listener) {
        mCountsClickListener = listener;
    }

    public void setDeckExpanderClickListener(View.OnClickListener listener) {
        mDeckExpanderClickListener = listener;
    }

    public void setDeckLongClickListener(View.OnLongClickListener listener) {
        mDeckLongClickListener = listener;
    }

    /** Sets whether the control should have partial transparency to allow a background to be seen */
    public void enablePartialTransparencyForBackground(boolean isTransparent) {
        mPartiallyTransparentForBackground = isTransparent;
    }


    /**
     * Consume a list of {@link DeckDueTreeNode}s to render a new deck list.
     */
    public void buildDeckList(List<DeckDueTreeNode> nodes, Collection col) {
        mCol = col;
        mDeckList.clear();
        mNew = mLrn = mRev = 0;
        mHasSubdecks = false;
        processNodes(nodes);
        notifyDataSetChanged();
    }


    @Override
    public DeckAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.deck_item, parent, false);
        return new ViewHolder(v);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // Update views for this node
        DeckDueTreeNode node = mDeckList.get(position);
        // Set the expander icon and padding according to whether or not there are any subdecks
        RelativeLayout deckLayout = holder.deckLayout;
        int rightPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_right_padding);
        if (mHasSubdecks) {
            int smallPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_left_padding_small);
            deckLayout.setPadding(smallPadding, 0, rightPadding, 0);
            holder.deckExpander.setVisibility(View.VISIBLE);
            // Create the correct expander for this deck
            setDeckExpander(holder.deckExpander, holder.deckLayout, holder.indentView, node);
        } else {
            holder.deckExpander.setVisibility(View.GONE);
            int normalPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_left_padding);
            deckLayout.setPadding(normalPadding, 0, rightPadding, 0);
        }

        if (node.hasChildren()) {
            holder.deckExpander.setTag(node.getDid());
            holder.deckExpander.setOnClickListener(mDeckExpanderClickListener);
            holder.deckLayout.setOnClickListener(mDeckExpanderClickListener);
        } else {
            holder.deckLayout.setOnClickListener(mCountsClickListener);
            holder.deckExpander.setOnClickListener(null);
            holder.countsLayout.setOnClickListener(mCountsClickListener);
        }
        holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
        // Set background colour. The current deck has its own color
        if (isCurrentlySelectedDeck(node)) {
            holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
            if (mPartiallyTransparentForBackground) {
                setBackgroundAlpha(holder.deckLayout, SELECTED_DECK_ALPHA_AGAINST_BACKGROUND);
            }
        } else {
            CompatHelper.getCompat().setSelectableBackground(holder.deckLayout);
        }
        // Set deck name and colour. Filtered decks have their own colour
        holder.deckName.setText(node.getLastDeckNameComponent());
        if (mCol.getDecks().isDyn(node.getDid())) {
            holder.deckName.setTextColor(mDeckNameDynColor);
        } else {
            holder.deckName.setTextColor(mDeckNameDefaultColor);
        }

        // Set the card counts and their colors
        holder.deckNew.setText(String.valueOf(node.getNewCount()));
        holder.deckNew.setTextColor((node.getNewCount() == 0) ? mZeroCountColor : mNewCountColor);
        holder.deckLearn.setText(String.valueOf(node.getLrnCount()));
        holder.deckLearn.setTextColor((node.getLrnCount() == 0) ? mZeroCountColor : mLearnCountColor);
        holder.deckRev.setText(String.valueOf(node.getRevCount()));
        holder.deckRev.setTextColor((node.getRevCount() == 0) ? mZeroCountColor : mReviewCountColor);

        // Store deck ID in layout's tag for easy retrieval in our click listeners
        holder.deckLayout.setTag(node.getDid());
        holder.countsLayout.setTag(node.getDid());

        // Set click listeners
        holder.deckLayout.setOnLongClickListener(mDeckLongClickListener);
    }


    private void setBackgroundAlpha(View view, @SuppressWarnings("SameParameterValue") double alphaPercentage) {
        Drawable background = view.getBackground().mutate();
        background.setAlpha((int) (255 * alphaPercentage));
        view.setBackground(background);
    }


    private boolean isCurrentlySelectedDeck(DeckDueTreeNode node) {
        return node.getDid() == mCol.getDecks().current().optLong("id");
    }


    @Override
    public int getItemCount() {
        return mDeckList.size();
    }


    private void setDeckExpander(ImageButton button, RelativeLayout expander, ImageButton indent, DeckDueTreeNode node){
        boolean collapsed = mCol.getDecks().get(node.getDid()).optBoolean("collapsed", false);
        // Apply the correct expand/collapse drawable
        if (collapsed) {
            button.setImageDrawable(mExpandImage);
            button.setContentDescription(expander.getContext().getString(R.string.expand));
            expander.setContentDescription(expander.getContext().getString(R.string.expand));
        } else if (node.hasChildren()) {
            button.setImageDrawable(mCollapseImage);
            button.setContentDescription(expander.getContext().getString(R.string.collapse));
            expander.setContentDescription(expander.getContext().getString(R.string.collapse));
        } else {
            button.setImageDrawable(mNoExpander);
        }
        // Add some indenting for each nested level
        int width = (int) indent.getResources().getDimension(R.dimen.keyline_1) * node.getDepth();
        indent.setMinimumWidth(width);
    }


    private void processNodes(List<DeckDueTreeNode> nodes) {
        for (DeckDueTreeNode node : nodes) {
            // If the default deck is empty, hide it by not adding it to the deck list.
            // We don't hide it if it's the only deck or if it has sub-decks.
            if (node.getDid() == 1 && nodes.size() > 1 && !node.hasChildren()) {
                if (mCol.getDb().queryScalar("select 1 from cards where did = 1") == 0) {
                    continue;
                }
            }
            // If any of this node's parents are collapsed, don't add it to the deck list
            for (Deck parent : mCol.getDecks().parents(node.getDid())) {
                mHasSubdecks = true;    // If a deck has a parent it means it's a subdeck so set a flag
                if (parent.optBoolean("collapsed")) {
                    return;
                }
            }
            mDeckList.add(node);

            // Add this node's counts to the totals if it's a parent deck
            if (node.getDepth() == 0) {
                mNew += node.getNewCount();
                mLrn += node.getLrnCount();
                mRev += node.getRevCount();
            }
            // Process sub-decks
            processNodes(node.getChildren());
        }
    }


    /**
     * Return the position of the deck in the deck list. If the deck is a child of a collapsed deck
     * (i.e., not visible in the deck list), then the position of the parent deck is returned instead.
     *
     * An invalid deck ID will return position 0.
     */
    public int findDeckPosition(long did) {
        for (int i = 0; i < mDeckList.size(); i++) {
            if (mDeckList.get(i).getDid() == did) {
                return i;
            }
        }
        // If the deck is not in our list, we search again using the immediate parent
        List<Deck> parents = mCol.getDecks().parents(did);
        if (parents.size() == 0) {
            return 0;
        } else {
            return findDeckPosition(parents.get(parents.size() - 1).optLong("id", 0));
        }
    }


    public int getEta() {
        return mCol.getSched().eta(new int[]{mNew, mLrn, mRev});
    }

    public int getDue() {
        return mNew + mLrn + mRev;
    }

    public List<DeckDueTreeNode> getDeckList() {
        return mDeckList;
    }
}
