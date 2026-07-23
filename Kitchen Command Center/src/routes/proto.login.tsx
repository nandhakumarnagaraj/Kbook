import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { Eye, EyeOff, Loader2, AlertCircle, CheckCircle2 } from "lucide-react";

export const Route = createFileRoute("/proto/login")({
  head: () => ({
    meta: [
      { title: "Login — KhanaBook Admin (Prototype)" },
      { name: "description", content: "Login screen prototype for KhanaBook Web Admin." },
      { property: "og:title", content: "KhanaBook Login Prototype" },
      { property: "og:description", content: "Design reference for the Angular login screen." },
    ],
  }),
  component: LoginProto,
});

type Variant = "default" | "loading" | "error" | "success" | "disabled";

function LoginProto() {
  const [variant, setVariant] = useState<Variant>("default");
  const [showPw, setShowPw] = useState(false);

  return (
    <div className="min-h-screen bg-background">
      <StateSwitcher variant={variant} setVariant={setVariant} />

      <div className="grid min-h-screen lg:grid-cols-2">
        {/* Brand pane */}
        <div className="relative hidden lg:flex flex-col justify-between overflow-hidden bg-espresso p-12 text-espresso-foreground">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-lg bg-saffron text-white font-display text-lg">
              K
            </div>
            <div className="font-display text-2xl">KhanaBook</div>
          </div>

          <div className="relative">
            <div className="absolute -left-6 -top-6 h-32 w-32 rounded-full bg-saffron/20 blur-3xl" />
            <div className="absolute right-0 top-20 h-40 w-40 rounded-full bg-burnt/20 blur-3xl" />
            <div className="relative">
              <div className="font-display text-4xl leading-tight text-cream">
                Every order, every table, every branch — one console.
              </div>
              <p className="mt-4 max-w-md text-sm leading-relaxed text-cream/70">
                Sign in to manage your restaurant's orders, menu, staff, devices, and marketplace
                integrations.
              </p>
            </div>
          </div>

          <div className="text-xs text-cream/50">© 2026 KhanaBook · v2.4.0</div>
        </div>

        {/* Form pane */}
        <div className="flex items-center justify-center p-6 sm:p-12">
          <div className="w-full max-w-sm">
            <div className="lg:hidden mb-8 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-lg bg-saffron text-white font-display text-lg">
                K
              </div>
              <div className="font-display text-2xl text-foreground">KhanaBook</div>
            </div>

            <h1 className="font-display text-3xl text-foreground">Welcome back</h1>
            <p className="mt-1 text-sm text-muted-foreground">Sign in to your restaurant admin</p>

            {variant === "success" && (
              <div className="mt-6 flex items-start gap-2 rounded-md border border-success/20 bg-success-soft p-3 text-sm text-success">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                <span>Signed in. Redirecting to your dashboard…</span>
              </div>
            )}
            {variant === "error" && (
              <div className="mt-6 flex items-start gap-2 rounded-md border border-danger/20 bg-danger-soft p-3 text-sm text-danger">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>Invalid login ID or password. Please try again.</span>
              </div>
            )}

            <form className="mt-6 space-y-4" onSubmit={(e) => e.preventDefault()}>
              <Field
                label="Login ID"
                htmlFor="login"
                error={variant === "error" ? "This ID is not recognised" : undefined}
              >
                <input
                  id="login"
                  type="text"
                  defaultValue="ravi@spicegarden.in"
                  disabled={
                    variant === "loading" || variant === "disabled" || variant === "success"
                  }
                  className={inputCls(variant === "error")}
                  placeholder="you@restaurant.in"
                />
              </Field>

              <Field
                label="Password"
                htmlFor="pw"
                trailing={
                  <Link
                    to="/proto/forgot"
                    className="text-xs font-medium text-saffron hover:underline"
                  >
                    Forgot?
                  </Link>
                }
              >
                <div className="relative">
                  <input
                    id="pw"
                    type={showPw ? "text" : "password"}
                    defaultValue="••••••••"
                    disabled={
                      variant === "loading" || variant === "disabled" || variant === "success"
                    }
                    className={`${inputCls(false)} pr-10`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPw((s) => !s)}
                    className="absolute right-2 top-1/2 grid h-7 w-7 -translate-y-1/2 place-items-center rounded text-muted-foreground hover:bg-muted"
                    aria-label={showPw ? "Hide password" : "Show password"}
                  >
                    {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </Field>

              <button
                type="submit"
                disabled={variant === "loading" || variant === "disabled" || variant === "success"}
                className="flex h-11 w-full items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-primary-foreground shadow-[var(--shadow-xs)] transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
              >
                {variant === "loading" && <Loader2 className="h-4 w-4 animate-spin" />}
                {variant === "loading" ? "Signing in…" : "Sign in"}
              </button>

              <div className="relative py-2">
                <div className="absolute inset-0 flex items-center">
                  <div className="w-full border-t border-border" />
                </div>
                <div className="relative flex justify-center">
                  <span className="bg-background px-2 text-xs text-muted-foreground">
                    or continue with
                  </span>
                </div>
              </div>

              <button
                type="button"
                disabled={variant === "loading" || variant === "disabled"}
                className="flex h-11 w-full items-center justify-center gap-3 rounded-md border border-input bg-surface text-sm font-medium hover:bg-muted disabled:cursor-not-allowed disabled:opacity-60"
              >
                <GoogleIcon />
                <span>Sign in with Google</span>
              </button>
            </form>

            <p className="mt-8 text-center text-xs text-muted-foreground">
              Need an account?{" "}
              <a href="#" className="font-medium text-saffron hover:underline">
                Contact your restaurant owner
              </a>
            </p>

            <div className="mt-6 text-center">
              <Link to="/" className="text-xs text-muted-foreground hover:text-foreground">
                ← Back to prototype index
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  htmlFor,
  error,
  trailing,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  trailing?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between">
        <label htmlFor={htmlFor} className="text-xs font-medium text-foreground">
          {label}
        </label>
        {trailing}
      </div>
      {children}
      {error && (
        <p className="mt-1.5 flex items-center gap-1 text-xs text-danger">
          <AlertCircle className="h-3 w-3" /> {error}
        </p>
      )}
    </div>
  );
}

function inputCls(isError: boolean) {
  return `h-11 w-full rounded-md border bg-surface px-3 text-sm outline-none transition-colors placeholder:text-muted-foreground focus-visible:ring-4 focus-visible:ring-ring disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-70 ${
    isError
      ? "border-danger focus-visible:border-danger"
      : "border-input focus-visible:border-saffron"
  }`;
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden>
      <path
        fill="#FFC107"
        d="M43.6 20.5H42V20H24v8h11.3C33.8 32.4 29.3 35.5 24 35.5c-6.4 0-11.5-5.1-11.5-11.5S17.6 12.5 24 12.5c2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.9 6.5 29.2 4.5 24 4.5 13.2 4.5 4.5 13.2 4.5 24S13.2 43.5 24 43.5 43.5 34.8 43.5 24c0-1.2-.1-2.4-.4-3.5z"
      />
      <path
        fill="#FF3D00"
        d="M6.3 14.7l6.6 4.8C14.6 15.6 18.9 12.5 24 12.5c2.9 0 5.6 1.1 7.6 2.9l5.7-5.7C33.9 6.5 29.2 4.5 24 4.5 16.3 4.5 9.6 8.9 6.3 14.7z"
      />
      <path
        fill="#4CAF50"
        d="M24 43.5c5.1 0 9.8-2 13.3-5.2l-6.1-5c-2 1.4-4.4 2.2-7.2 2.2-5.3 0-9.7-3.1-11.3-7.5l-6.5 5C9.5 39 16.2 43.5 24 43.5z"
      />
      <path
        fill="#1976D2"
        d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.2-4.2 5.5l6.1 5c-.4.4 6.8-5 6.8-14.5 0-1.2-.1-2.4-.4-3.5z"
      />
    </svg>
  );
}

function StateSwitcher({
  variant,
  setVariant,
}: {
  variant: Variant;
  setVariant: (v: Variant) => void;
}) {
  const opts: Variant[] = ["default", "loading", "error", "success", "disabled"];
  return (
    <div className="fixed left-1/2 top-4 z-50 -translate-x-1/2 rounded-full border border-border bg-surface p-1 shadow-[var(--shadow-md)]">
      {opts.map((o) => (
        <button
          key={o}
          onClick={() => setVariant(o)}
          className={`rounded-full px-3 py-1 text-xs font-medium capitalize transition-colors ${
            variant === o
              ? "bg-espresso text-espresso-foreground"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {o}
        </button>
      ))}
    </div>
  );
}
