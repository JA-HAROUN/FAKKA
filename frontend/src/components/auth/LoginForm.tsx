import { PasswordInput } from "@/components/auth/PasswordInput";
import { Field, FormError } from "@/components/common/Field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useApp } from "@/context/AppContext";
import { useState } from "react";

export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const { signIn } = useApp();
  const [email, setEmail] = useState("john@fakka.app");
  const [password, setPassword] = useState("demo1234");
  const [errors, setErrors] = useState<{ email?: string; password?: string; form?: string }>({});

  function submit() {
    const next: typeof errors = {};
    if (!email.trim()) next.email = "Enter the email you signed up with.";
    if (!password.trim()) next.password = "Enter your password.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    void signIn(email, password)
      .then(onSuccess)
      .catch((error: unknown) => {
        setErrors({ form: error instanceof Error ? error.message : "Sign-in failed. Try again." });
      });
  }

  return (
    <form
      className="space-y-5"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <Field label="Email" error={errors.email}>
        {(field) => (
          <Input
            {...field}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        )}
      </Field>

      <Field label="Password" error={errors.password}>
        {(field) => (
          <PasswordInput
            {...field}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Your password"
          />
        )}
      </Field>

      <FormError message={errors.form} />

      <Button type="submit" size="lg" className="w-full">
        Sign in
      </Button>

      <p className="text-center text-caption text-muted-foreground">
        Use the password you chose when you created your account.
      </p>
    </form>
  );
}
