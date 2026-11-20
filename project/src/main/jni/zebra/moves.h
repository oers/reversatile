/*
   File:           moves.h

   Created:        June 30, 1997

   Author:         Gunnar Andersson (gunnar@radagast.se)

   Contents:       The move generator's interface.
*/



#ifndef MOVES_H
#define MOVES_H



#include "constant.h"



#ifdef __cplusplus
extern "C" {
#endif



/* The number of disks played from the initial position.
   Must match the current status of the BOARD variable. */
extern _Thread_local int disks_played;

/* Holds the last move made on the board for each different
   game stage. */
extern int last_move[65];

/* The number of moves available after a certain number
   of disks played. */
extern _Thread_local int move_count[MAX_SEARCH_DEPTH];

/* The actual moves available after a certain number of
   disks played. */
extern _Thread_local int move_list[MAX_SEARCH_DEPTH][64];

/* Directional flip masks for all board positions. */
extern const int dir_mask[100];

/* Increments corresponding to the different flip directions */
extern const int move_offset[8];

/* The directions in which there exist valid moves. If there are N such
   directions for a square, the Nth element of the list is 0. */
extern int flip_direction[100][16];

/* Pointers to FLIPDIRECTION[][0]. */
extern int *first_flip_direction[100];


void
bitboard_move_generation( int side_to_move );

void
init_moves( void );

int
generate_specific( int curr_move, int side_to_move );

int
generate_move( int side_to_move );

void
generate_all( int side_to_move );

int
count_all( int side_to_move, int empty );

int
game_in_progress( void );

int
make_move( int side_to_move, int move, int update_hash );

/* Everything make_move() and unmake_move() keep track of: the board and
   what it takes to take the moves played so far back. */
typedef struct {
  int board[128];
  int disks_played;
  int flip_depth;
  int *flips[2048];
  int flip_count[65];
  unsigned int hash1, hash2;
  unsigned int hash_stored1[MAX_SEARCH_DEPTH];
  unsigned int hash_stored2[MAX_SEARCH_DEPTH];
  int piece_count[3][MAX_SEARCH_DEPTH];
} PositionState;

void
save_position( PositionState *state );

/* Where a search first left the move count different from what it was
   called with, if one did since drift_where was last cleared - to find
   the search behind a changed position (see _droidzebra_compute_evals). */
extern const char *drift_where;
extern int drift_expected, drift_found;

void
drift_check( int expected_disks_played, const char *where );

int
restore_position_if_changed( const PositionState *state );

int
make_move_no_hash( int side_to_move, int move );

void
unmake_move( int side_to_move, int move );

void
unmake_move_no_hash( int side_to_move, int move );

int
valid_move( int move, int side_to_move );

int
get_move( int side_to_move );



#ifdef __cplusplus
}
#endif



#endif  /* MOVES_H */
