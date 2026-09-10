import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/common/Field";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { useApp } from "@/context/AppContext";

const MIN_PASSWORD_LENGTH = 4;

export function RegisterForm({ onSuccess }: { onSuccess: () => void }) {
  const { signUp } = useApp();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ name?: string; email?: string; password?: string }>({});

  function submit() {
    const next: typeof errors = {};
    if (!name.trim()) next.name = "Tell us what to call you.";
    if (!email.trim()) next.email = "Enter your email address.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
      next.email = "That doesn't look like a valid email address.";
    if (password.length < MIN_PASSWORD_LENGTH)
      next.password = `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    signUp(name, email);
    onSuccess();
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
      <Field label="Full name" error={errors.name}>
        {(field) => (
          <Input
            {...field}
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Nour Ibrahim"
          />
        )}
      </Field>

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

      <Field
        label="Password"
        error={errors.password}
        hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
      >
        {(field) => (
          <PasswordInput
            {...field}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Create a password"
          />
        )}
      </Field>

      <Button type="submit" size="lg" className="w-full">
        Create account
      </Button>
    </form>
  );
}
