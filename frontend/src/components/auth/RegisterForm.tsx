import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useApp } from "@/context/AppContext";

export function RegisterForm({ onSuccess }: { onSuccess: () => void }) {
  const { signUp } = useApp();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!name.trim() || !email.trim() || password.length < 4) {
          setError("Fill in your name, email and a password of at least 4 characters.");
          return;
        }
        signUp(name, email);
        onSuccess();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="signup-name">Full name</Label>
        <Input
          id="signup-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Nour Ibrahim"
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="signup-email">Email</Label>
        <Input
          id="signup-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="signup-password">Password</Label>
        <Input
          id="signup-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="At least 4 characters"
        />
      </div>
      {error && <p className="text-sm text-negative">{error}</p>}
      <Button type="submit" className="w-full" size="lg">
        Create account
      </Button>
    </form>
  );
}
