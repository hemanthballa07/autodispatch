"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function ProfilePage() {
  const router = useRouter();
  const [name, setName] = useState<string | null>(null);
  const [phone, setPhone] = useState<string | null>(null);

  useEffect(() => {
    setName(localStorage.getItem("autodispatch_name"));
    setPhone(localStorage.getItem("autodispatch_phone"));
  }, []);

  function signOut() {
    localStorage.removeItem("autodispatch_token");
    localStorage.removeItem("autodispatch_name");
    localStorage.removeItem("autodispatch_phone");
    router.push("/book");
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4">
      <Card className="w-full max-w-sm shadow-sm">
        <CardHeader>
          <CardTitle className="text-xl">Profile</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {name || phone ? (
            <div className="flex flex-col gap-1 text-sm">
              {name && (
                <p>
                  <span className="font-medium text-foreground">Name: </span>
                  <span className="text-muted-foreground">{name}</span>
                </p>
              )}
              {phone && (
                <p>
                  <span className="font-medium text-foreground">Phone: </span>
                  <span className="text-muted-foreground">{phone}</span>
                </p>
              )}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">No session active.</p>
          )}

          <Link
            href="/history"
            className="text-sm font-medium text-[#106344] hover:underline"
          >
            View ride history →
          </Link>

          <Button
            variant="outline"
            onClick={signOut}
            className="w-full border-red-300 text-red-600 hover:bg-red-50 hover:text-red-700"
          >
            Sign out
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
