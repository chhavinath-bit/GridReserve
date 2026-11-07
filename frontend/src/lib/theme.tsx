import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

// Theme preference: system, light or dark (DESIGN_SYSTEM §4.2 ThemeToggle, §8). The preference is the
// only thing this app keeps in localStorage. index.html applies it before the first paint.
export type ThemePreference = 'system' | 'light' | 'dark'
type Resolved = 'light' | 'dark'

const STORAGE_KEY = 'gr-theme'

interface ThemeState {
  preference: ThemePreference
  resolved: Resolved
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeState | null>(null)

function readPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}

function systemPrefersDark(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readPreference)
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  useEffect(() => {
    if (typeof matchMedia !== 'function') return
    const query = matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystemDark(query.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const resolved: Resolved = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference

  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
  }, [resolved])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Private mode or blocked storage: the choice still applies for this visit.
    }
  }, [])

  const value = useMemo(() => ({ preference, resolved, setPreference }), [preference, resolved, setPreference])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeState {
  const state = useContext(ThemeContext)
  if (!state) throw new Error('useTheme must be used inside ThemeProvider')
  return state
}
