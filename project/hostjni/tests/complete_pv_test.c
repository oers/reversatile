/*
 * Plain C test of the engine core's complete_pv(), without JNI: the case
 * behind a lost undo/redo in CI ("Error in PV completion") depends on a
 * search being interrupted at an exact moment, which a JVM test can't hit
 * on purpose. Built and run by "make check" (see the Makefile).
 *
 * Usage: complete_pv_test <assets dir with coeffs2.bin>
 */
#include <setjmp.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "zebra/macros.h"
#include "zebra/globals.h"
#include "zebra/game.h"
#include "zebra/search.h"
#include "zebra/threads.h"

/* The engine core reports to the JNI layer; nothing to report to here. */
char android_files_dir[256];
int droidzebra_message_debug(const char *format, ...) { return 0; }
void droidzebra_msg_board(void) {}
void droidzebra_msg_candidate_evals(void) {}
void droidzebra_msg_eval(void) {}
void droidzebra_msg_pv(void) {}
void droidzebra_msg_status(void) {}
void handle_event(int only_passive_events, int allow_delay, int passive_mode) {}
void toggle_event_status(int allow_event_handling) {}

static jmp_buf on_fatal;
static char fatal_text[256];

void fatal_error(const char *format, ...) {
    va_list ap;
    va_start(ap, format);
    vsnprintf(fatal_text, sizeof fatal_text, format, ap);
    va_end(ap);
    longjmp(on_fatal, 1);
}

/* The engine headers turn printf into its debug message on Android builds. */
static void report(const char *format, ...) {
    char text[512];
    va_list ap;
    va_start(ap, format);
    vsnprintf(text, sizeof text, format, ap);
    va_end(ap);
    if (write(1, text, strlen(text)) < 0) {
        /* nothing better to do */
    }
}

static int failures = 0;

/* Runs complete_pv() on the start position with the given PV and checks
   the completed line and that the board is left as it was. */
static void check(const char *name, const int *moves, int count,
                  const int *expected, int expected_count) {
    int side = BLACKSQ, i, saved[100];

    game_init(NULL, &side);
    for (i = 0; i < 100; i++)
        saved[i] = board[i];
    for (i = 0; i < count; i++)
        pv[0][i] = moves[i];
    pv_depth[0] = count;

    if (setjmp(on_fatal)) {
        report("FAIL %s: complete_pv() raised \"%s\"\n", name, fatal_text);
        failures++;
        return;
    }
    complete_pv(side);

    for (i = 0; i < 100; i++)
        if (board[i] != saved[i]) {
            report("FAIL %s: board not restored (square %d)\n", name, i);
            failures++;
            return;
        }
    if (full_pv_depth != expected_count) {
        report("FAIL %s: completed line has %d moves, expected %d\n", name, full_pv_depth, expected_count);
        failures++;
        return;
    }
    for (i = 0; i < expected_count; i++)
        if (full_pv[i] != expected[i]) {
            report("FAIL %s: move %d of the completed line is %d, expected %d\n", name, i, full_pv[i], expected[i]);
            failures++;
            return;
        }
    report("PASS %s\n", name);
}

int main(int argc, char **argv) {
    int side;

    if (argc > 1)
        snprintf(android_files_dir, sizeof android_files_dir, "%s", argv[1]);
    if (setjmp(on_fatal)) {
        report("FAIL setup: \"%s\"\n", fatal_text);
        return 2;
    }
    threads_init(1);
    global_setup(0, 10);
    game_init(NULL, &side);

    /* squares are 10 * row + column: f5 = 56, f6 = 66, c3 = 33 */
    {
        const int line[] = {56, 66};
        check("a line that fits is completed as it is", line, 2, line, 2);
    }
    {
        /* c3 is no move for either side after f5 f6 - as when the PV is
           left over from a search on another position */
        const int line[] = {56, 66, 33};
        const int fitting[] = {56, 66};
        check("a line that stops fitting is cut off there, not a fatal error", line, 3, fitting, 2);
    }
    {
        const int line[] = {33, 56};
        check("a line that doesn't fit at all leaves an empty line", line, 2, line, 0);
    }
    return failures == 0 ? 0 : 1;
}
