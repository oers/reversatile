/*
   File:       globals.c

   Created:    June 30, 1997

   Author:     Gunnar Andersson (gunnar@radagast.se)

   Contents:   Global state variables.
*/



#include "globals.h"


/* Global variables */

_Thread_local int pv[MAX_SEARCH_DEPTH][MAX_SEARCH_DEPTH];
_Thread_local int pv_depth[MAX_SEARCH_DEPTH];
int score_sheet_row;
_Thread_local int piece_count[3][MAX_SEARCH_DEPTH];
int black_moves[60];
int white_moves[60];
_Thread_local Board board;
