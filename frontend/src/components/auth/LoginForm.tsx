import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useApp } from "@/context/AppContext";

export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const { signIn } = useApp();
  const [email, setEmail] = useState("john@splitease.app");
  const [password, setPassword] = useState("demo1234");
  const [error, setError] = useState("");

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!email.trim() || !password.trim()) {
          setError("Enter your email and password.");
          return;
        }
        signIn(email);
        onSuccess();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="login-email">Email</Label>
        <Input
          id="login-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="login-password">Password</Label>
        <Input
          id="login-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="••••••••"
        />
      </div>
      {error && <p className="text-sm text-negative">{error}</p>}
      <Button type="submit" className="w-full" size="lg">
        Sign in
      </Button>
      <p className="text-center text-xs text-muted-foreground">
        Demo mode — any password works with a sample account.
      </p>
    </form>
  );
}
