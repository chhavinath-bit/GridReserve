import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/api/client'
import { TEMPORARY_CODES } from '@/api/errors'

// Reads retry once, and only for temporary failures (a 4xx will give the same answer again).
// Writes never retry automatically (CLAUDE.md §7.4): a hold is not idempotent until M1.12.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: (failureCount, error) => failureCount < 1 && error instanceof ApiError && TEMPORARY_CODES.has(error.code),
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
})
