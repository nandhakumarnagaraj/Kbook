import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { ArrowLeft, KeyRound, ShieldCheck, CheckCircle2 } from "lucide-react";
import { Btn, StateSwitcher } from "../proto/primitives";

export const Route = createFileRoute("/proto/forgot")({
  head: () => ({
    meta: [
      { title: "Reset password — KhanaBook (Prototype)" },
      { name: "description", content: "Forgot password + OTP flow prototype." },
      { property: "og:title", content: "KhanaBook Reset Password" },
      { property: "og:description", content: "OTP-based password reset design reference." },
    ],
  }),
  component: ForgotProto,
});

type Step = "request" | "otp" | "reset" | "done";

function ForgotProto() {
  const [step, setStep] = useState<Step>("request");
  return (
    <div className="min-h-screen bg-background">
      <StateSwitcher
        value={step}
        onChange={setStep}
        options={["request", "otp", "reset", "done"] as const}
        position="top"
        label="Step"
      />

      <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center p-6">
        <div className="mb-8 flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-lg bg-saffron text-white font-display text-lg">
            K
          </div>
          <div className="font-display text-2xl text-foreground">KhanaBook</div>
        </div>

        <div className="w-full rounded-xl border border-border bg-surface p-6 shadow-[var(--shadow-sm)]">
          {step === "request" && (
            <>
              <div className="mb-1 grid h-10 w-10 place-items-center rounded-md bg-primary-soft text-saffron">
                <KeyRound className="h-5 w-5" />
              </div>
              <h1 className="text-xl font-semibold tracking-tight">Reset your password</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Enter your registered email or phone and we'll send you a one-time code.
              </p>
              <form
                className="mt-5 space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  setStep("otp");
                }}
              >
                <Field label="Email or phone">
                  <input
                    type="text"
                    defaultValue="ravi@spicegarden.in"
                    className="h-11 w-full rounded-md border border-input bg-surface px-3 text-sm outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
                  />
                </Field>
                <Btn variant="primary" className="w-full h-11" type="submit">
                  Send OTP
                </Btn>
              </form>
            </>
          )}
          {step === "otp" && (
            <>
              <div className="mb-1 grid h-10 w-10 place-items-center rounded-md bg-info-soft text-info">
                <ShieldCheck className="h-5 w-5" />
              </div>
              <h1 className="text-xl font-semibold tracking-tight">Enter verification code</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                A 6-digit code was sent to{" "}
                <span className="font-medium text-foreground">ravi@spicegarden.in</span>. Expires in
                5:00.
              </p>
              <div className="mt-5 flex justify-between gap-2">
                {[3, 7, 4, 9, 1, 2].map((n, i) => (
                  <input
                    key={i}
                    defaultValue={n}
                    maxLength={1}
                    className="h-12 w-12 rounded-md border border-input bg-surface text-center text-lg font-semibold tabular outline-none focus-visible:border-saffron focus-visible:ring-4 focus-visible:ring-ring"
                  />
                ))}
              </div>
              <div className="mt-3 flex items-center justify-between text-xs">
                <button className="text-muted-foreground hover:text-foreground">
                  Didn't get it? Resend in 42s
                </button>
              </div>
              <Btn variant="primary" className="mt-5 w-full h-11" onClick={() => setStep("reset")}>
                Verify
              </Btn>
            </>
          )}
          {step === "reset" && (
            <>
              <h1 className="text-xl font-semibold tracking-tight">Set a new password</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Use at least 10 characters with a mix of letters, numbers, and symbols.
              </p>
              <form
                className="mt-5 space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  setStep("done");
                }}
              >
                <Field label="New password">
                  <input
                    type="password"
                    defaultValue="••••••••••"
                    className="h-11 w-full rounded-md border border-input bg-surface px-3 text-sm"
                  />
                </Field>
                <Field label="Confirm new password">
                  <input
                    type="password"
                    defaultValue="••••••••••"
                    className="h-11 w-full rounded-md border border-input bg-surface px-3 text-sm"
                  />
                </Field>
                <div className="rounded-md bg-muted p-3 text-xs text-muted-foreground">
                  Signing in on all your other devices will end. You'll need to log in again.
                </div>
                <Btn variant="primary" className="w-full h-11" type="submit">
                  Update password
                </Btn>
              </form>
            </>
          )}
          {step === "done" && (
            <>
              <div className="grid h-12 w-12 place-items-center rounded-full bg-success-soft text-success">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <h1 className="mt-3 text-xl font-semibold tracking-tight">Password updated</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                You can now sign in with your new password.
              </p>
              <Link to="/proto/login">
                <Btn variant="primary" className="mt-5 w-full h-11">
                  Back to sign in
                </Btn>
              </Link>
            </>
          )}
        </div>

        <Link
          to="/proto/login"
          className="mt-6 inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-3 w-3" /> Back to sign in
        </Link>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1.5 block text-xs font-medium text-foreground">{label}</label>
      {children}
    </div>
  );
}
