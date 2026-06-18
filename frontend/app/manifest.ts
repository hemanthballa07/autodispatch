import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "AutoDispatch",
    short_name: "AutoDispatch",
    description: "Auto-rickshaw rides on campus — book in one tap.",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#106344",
    icons: [
      {
        src: "/icon-192.png",
        sizes: "192x192",
        type: "image/png",
      },
      {
        src: "/icon-512.png",
        sizes: "512x512",
        type: "image/png",
      },
    ],
  };
}
