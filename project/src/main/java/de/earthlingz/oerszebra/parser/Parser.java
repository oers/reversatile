package de.earthlingz.oerszebra.parser;

import com.shurik.droidzebra.Move;

import java.util.LinkedList;

/**
 * Created by stefan on 18.03.2018.
 */

interface Parser {
    LinkedList<Move> makeMoveList(String s);
}
