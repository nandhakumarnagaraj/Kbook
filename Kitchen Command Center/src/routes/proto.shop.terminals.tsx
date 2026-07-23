import { createFileRoute } from "@tanstack/react-router";
import { TerminalsScreen } from "../proto/terminals";

export const Route = createFileRoute("/proto/shop/terminals")({
  head: () => ({
    meta: [
      { title: "Devices — SHOP_ADMIN · KhanaBook" },
      { name: "description", content: "Terminal fleet as seen by SHOP_ADMIN." },
      { property: "og:title", content: "KhanaBook Devices (SHOP_ADMIN)" },
      { property: "og:description", content: "SHOP_ADMIN-only view of the same terminal console." },
    ],
  }),
  component: () => <TerminalsScreen role="SHOP_ADMIN" />,
});
