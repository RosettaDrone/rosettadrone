/* Include the correct ffitarget.h automatically. This helps us create prefixes
 * with multi-lib Linux and OSX/iOS universal builds. To avoid listing all
 * possible architectures here, we try the configured target arch first and then
 * include the most common multilib/universal setups in the #elif ladder */
#ifdef __x86_64__
#include "ffitarget-x86_64.h"
#elif defined(__i386__) || defined(_M_IX86)
#include "ffitarget-x86.h"
#elif defined(__x86_64__) || defined(_M_X64)
#include "ffitarget-x86_64.h"
#elif defined(__arm__) || defined(_M_ARM)
#include "ffitarget-arm.h"
#elif defined(__aarch64__)
#include "ffitarget-aarch64.h"
#elif defined(__powerpc__) || defined(_M_PPC)
#include "ffitarget-powerpc.h"
#elif defined(__powerpc64__)
#include "ffitarget-powerpc64.h"
#else
#error "Unsupported Architecture"
#endif
