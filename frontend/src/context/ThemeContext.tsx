import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "fakka.theme";

/**
 * Runs before first paint, inlined into <head> by the root route.
 *
 * The server can't know which theme this visitor picked, so without this the
 * page would render light and then snap to dark on hydration. Reading storage
 * (falling back to the OS preference) and stamping `.dark` on <html> here means
 * the very first frame is already correct.
 *
 * Keep this in sync with `resolveInitialTheme` below — same rule, twice, once
 * for the browser boot and once for React.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var s=localStorage.getItem(${JSON.stringify(
  STORAGE_KEY,
)});var d=s?s==="dark":window.matchMedia("(prefers-color-scheme: dark)").matches;if(d){document.documentElement.classList.add("dark")}}catch(e){}})();`;

/** The colour behind the browser's own chrome on mobile — matches --background. */
const THEME_COLOR: Record<Theme, string> = {
  light: "#f7f9fa",
  dark: "#12161c",
};

function storedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === "light" || value === "dark" ? value : null;
  } catch {
    return null;
  }
}

function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle("dark", theme === "dark");
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", THEME_COLOR[theme]);
}

interface ThemeContextValue {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Light/dark for the whole app. The choice persists in localStorage; until the
 * user makes one, the OS preference wins and keeps winning as it changes.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  // SSR has to render *something* deterministic. The real value is adopted from
  // the DOM on mount — the boot script above has already decided it.
  const [theme, setThemeState] = useState<Theme>("light");

  useEffect(() => {
    const active: Theme = document.documentElement.classList.contains("dark") ? "dark" : "light";
    setThemeState(active);
    applyTheme(active);
  }, []);

  useEffect(() => {
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = (event: MediaQueryListEvent) => {
      // An explicit choice outranks the OS; only follow along until one exists.
      if (storedTheme()) return;
      const next: Theme = event.matches ? "dark" : "light";
      applyTheme(next);
      setThemeState(next);
    };
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  const setTheme = useCallback((next: Theme) => {
    applyTheme(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* private mode / storage full — the theme still applies for this visit */
    }
    setThemeState(next);
  }, []);

  // Read the live DOM rather than state, so the first click is correct even if
  // it lands before the adopt-on-mount effect has run.
  const toggleTheme = useCallback(() => {
    setTheme(document.documentElement.classList.contains("dark") ? "light" : "dark");
  }, [setTheme]);

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, setTheme, toggleTheme }),
    [theme, setTheme, toggleTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
