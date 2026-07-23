import { createFileRoute } from "@tanstack/react-router";
import { TerminalsScreen } from "../proto/terminals";

export const Route = createFileRoute("/proto/owner/terminals")({
  head: () => ({
    meta: [
      { title: "Devices — Spice Garden · KhanaBook" },
      {
        name: "description",
        content: "Terminal fleet, activation requests and recovery prototype.",
      },
      { property: "og:title", content: "KhanaBook Devices" },
      { property: "og:description", content: "Design reference for terminal fleet management." },
    ],
  }),
  component: () => <TerminalsScreen role="OWNER" />,
});
