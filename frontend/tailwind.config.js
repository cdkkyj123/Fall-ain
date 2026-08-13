/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        blush: {
          50: "#fff7f8",
          100: "#ffeef1",
          200: "#ffd9e0",
          300: "#ffbecb",
          400: "#fb9cae",
          500: "#f17c93",
          600: "#dd5f7a",
          700: "#b84761",
        },
        lavender: {
          50: "#f8f7ff",
          100: "#efecff",
          200: "#ded8ff",
          300: "#c6bbff",
          400: "#a998f5",
          500: "#8d79de",
          600: "#6f5cc0",
        },
        cream: {
          50: "#fffdf8",
          100: "#fdf6ec",
          200: "#f7ecdb",
        },
      },
      fontFamily: {
        sans: [
          "Pretendard",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "sans-serif",
        ],
      },
      boxShadow: {
        soft: "0 8px 30px -12px rgba(180, 120, 140, 0.35)",
      },
    },
  },
  plugins: [],
};
