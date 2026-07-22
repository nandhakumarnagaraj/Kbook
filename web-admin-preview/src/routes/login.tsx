import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: "Sign in — KhanaBook Admin" },
      { name: "description", content: "Sign in to your KhanaBook admin workspace." },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");

  return (
    <div className="min-h-screen grid lg:grid-cols-2 bg-background">
      {/* Left brand panel */}
      <div
        className="relative hidden lg:flex flex-col justify-between p-12 text-primary-foreground"
        style={{ backgroundImage: "var(--gradient-hero)" }}
      >
        <div>
          <div className="flex items-center gap-3">
            <div className="h-11 w-11 rounded-xl bg-white/15 backdrop-blur grid place-items-center font-display font-bold text-lg">
              K
            </div>
            <div className="font-display font-bold text-lg">KhanaBook</div>
          </div>
        </div>

        <div className="max-w-md space-y-6">
          <h1 className="font-display font-extrabold text-4xl leading-tight tracking-tight">
            Run your kitchen<br />with confidence.
          </h1>
          <p className="text-primary-foreground/85 text-base">
            One workspace for menu, orders, staff, and payments — built for busy owners and managers.
          </p>

          <ul className="space-y-3 pt-4">
            {[
              "Live orders across POS & marketplaces",
              "Menu & stock control in seconds",
              "Daily revenue & refund insights",
            ].map((line, i) => (
              <li key={line} className="flex items-start gap-3">
                <span className="mt-0.5 h-6 w-6 rounded-md bg-white/15 grid place-items-center text-xs font-bold">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <span className="text-sm text-primary-foreground/90">{line}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="text-xs text-primary-foreground/70">© 2026 KhanaBook. All rights reserved.</div>
      </div>

      {/* Right auth card */}
      <div className="flex items-center justify-center p-6 md:p-10">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center gap-3 mb-8">
            <div className="h-10 w-10 rounded-xl grid place-items-center text-primary-foreground font-display font-bold" style={{ backgroundImage: "var(--gradient-primary)" }}>K</div>
            <div className="font-display font-bold">KhanaBook</div>
          </div>

          <h2 className="font-display font-bold text-2xl">Welcome back</h2>
          <p className="text-sm text-muted-foreground mt-1">Sign in to your admin workspace.</p>

          <div className="mt-6 flex items-center gap-3 text-xs text-muted-foreground">
            <div className="flex-1 h-px bg-border" />
            <span>or with password</span>
            <div className="flex-1 h-px bg-border" />
          </div>

          <form
            className="mt-6 space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              navigate({ to: "/business/dashboard" });
            }}
          >
            <div>
              <label className="text-xs font-semibold text-foreground">Login ID</label>
              <input
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                placeholder="Phone number or email"
                className="mt-1.5 w-full h-11 rounded-lg border border-input bg-card px-3.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
              />
            </div>
            <div>
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-foreground">Password</label>
                <button type="button" className="text-xs font-semibold text-primary hover:underline">
                  Forgot password?
                </button>
              </div>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter password"
                className="mt-1.5 w-full h-11 rounded-lg border border-input bg-card px-3.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <button
              type="submit"
              className="w-full h-11 rounded-lg font-semibold text-sm text-primary-foreground shadow-[var(--shadow-elevated)] transition-transform hover:-translate-y-0.5"
              style={{ backgroundImage: "var(--gradient-primary)" }}
            >
              Sign in
            </button>
          </form>

          <p className="mt-8 text-[11px] text-muted-foreground text-center">
            Logged in as <span className="font-semibold text-foreground">OWNER</span> · demo preview
          </p>
        </div>
      </div>
    </div>
  );
}
