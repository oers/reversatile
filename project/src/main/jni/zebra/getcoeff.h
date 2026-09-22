/*
   File:         getcoeff.h

   Created:      November 20, 1997

   Author:       Gunnar Andersson (gunnar@radagast.se)

   Contents:
*/



#ifndef GETCOEFF_H
#define GETCOEFF_H



#ifdef __cplusplus
extern "C" {
#endif



void
init_coeffs( void );

void
remove_coeffs( int phase );

void
clear_coeffs( void );

int
pattern_evaluation( int side_to_move );



#ifdef __cplusplus
}
#endif



#endif  /* GETCOEFF_H */
