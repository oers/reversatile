/*
   File:          threads.c

   Created:       August 9, 2026

   Contents:      A small fork-join worker pool for the parallel search.

                  The pool is deliberately minimal: the caller hands in
                  a job function and a job count, and every thread --
                  the caller included -- claims jobs off a shared
                  counter until they run out.  That keeps the work
                  balanced without a queue, and it means a search can
                  fall back to running everything on the calling thread
                  simply by asking for one thread.
*/



#include <pthread.h>
#include <stdlib.h>

#include "constant.h"
#include "search.h"
#include "threads.h"



#define MAX_THREADS               64



typedef struct {
  pthread_mutex_t lock;
  pthread_cond_t work_ready;
  pthread_cond_t work_done;
  void (*job)( int index, void *context );
  void *context;
  int job_count;
  int next_job;
  int busy_count;      /* workers still inside the current batch */
  int generation;      /* bumped once per batch, so wake-ups are unambiguous */
  int shutting_down;
} PoolState;



/* Local variables */

static PoolState pool;
static pthread_t worker[MAX_THREADS];
static int thread_count = 1;
static int worker_count;   /* threads in worker[], i.e. thread_count - 1 */
static int pool_created;
static _Thread_local int is_worker;



/*
  CLAIM_JOBS
  Run jobs off the shared counter until the batch is exhausted.
  Called with the lock held; releases it while a job runs.
*/

static void
claim_jobs( int generation ) {
  while ( TRUE ) {
    int index;

    if ( (pool.generation != generation) || (pool.next_job >= pool.job_count) )
      return;
    index = pool.next_job++;

    pthread_mutex_unlock( &pool.lock );
    pool.job( index, pool.context );
    pthread_mutex_lock( &pool.lock );
  }
}


/*
  WORKER_MAIN
  Wait for a batch, take part in it, and report back when it is done.
*/

static void *
worker_main( void *arg ) {
  int last_generation = 0;

  (void) arg;
  is_worker = TRUE;
  init_search_thread();

  pthread_mutex_lock( &pool.lock );
  while ( TRUE ) {
    while ( !pool.shutting_down && (pool.generation == last_generation) )
      pthread_cond_wait( &pool.work_ready, &pool.lock );
    if ( pool.shutting_down )
      break;
    last_generation = pool.generation;

    claim_jobs( last_generation );

    if ( --pool.busy_count == 0 )
      pthread_cond_signal( &pool.work_done );
  }
  pthread_mutex_unlock( &pool.lock );

  return NULL;
}



void
threads_init( int count ) {
  int i;

  if ( count < 1 )
    count = 1;
  if ( count > MAX_THREADS )
    count = MAX_THREADS;
  if ( pool_created && (count == thread_count) )
    return;

  threads_shutdown();

  thread_count = count;
  worker_count = count - 1;
  if ( worker_count == 0 ) {
    pool_created = TRUE;
    return;
  }

  pthread_mutex_init( &pool.lock, NULL );
  pthread_cond_init( &pool.work_ready, NULL );
  pthread_cond_init( &pool.work_done, NULL );
  pool.generation = 0;
  pool.shutting_down = FALSE;
  pool.busy_count = 0;

  for ( i = 0; i < worker_count; i++ )
    if ( pthread_create( &worker[i], NULL, worker_main, NULL ) != 0 ) {
      /* Carry on with however many threads we did get */
      worker_count = i;
      thread_count = i + 1;
      break;
    }
  pool_created = TRUE;
}


void
threads_shutdown( void ) {
  int i;

  if ( !pool_created )
    return;
  if ( worker_count > 0 ) {
    pthread_mutex_lock( &pool.lock );
    pool.shutting_down = TRUE;
    pthread_cond_broadcast( &pool.work_ready );
    pthread_mutex_unlock( &pool.lock );

    for ( i = 0; i < worker_count; i++ )
      pthread_join( worker[i], NULL );

    pthread_cond_destroy( &pool.work_done );
    pthread_cond_destroy( &pool.work_ready );
    pthread_mutex_destroy( &pool.lock );
  }
  worker_count = 0;
  thread_count = 1;
  pool_created = FALSE;
}


int
threads_count( void ) {
  return thread_count;
}


int
threads_is_worker( void ) {
  return is_worker;
}


void
threads_run( void (*job)( int index, void *context ), void *context,
	     int job_count ) {
  int i;

  if ( job_count <= 0 )
    return;

  if ( worker_count == 0 ) {  /* Single-threaded: just run them here */
    for ( i = 0; i < job_count; i++ )
      job( i, context );
    return;
  }

  pthread_mutex_lock( &pool.lock );
  pool.job = job;
  pool.context = context;
  pool.job_count = job_count;
  pool.next_job = 0;
  pool.busy_count = worker_count + 1;   /* the workers plus the caller */
  pool.generation++;
  pthread_cond_broadcast( &pool.work_ready );

  /* The caller helps rather than idling.  It is up to the caller to put
     its own search state back afterwards, since a job overwrites the
     state it was using, and not to start a nested batch from inside a
     job -- the pool is already busy with this one. */
  claim_jobs( pool.generation );

  if ( --pool.busy_count > 0 )
    while ( pool.busy_count > 0 )
      pthread_cond_wait( &pool.work_done, &pool.lock );
  pthread_mutex_unlock( &pool.lock );
}
