/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        './pages/**/*.{js,ts,jsx,tsx,mdx}',
        './components/**/*.{js,ts,jsx,tsx,mdx}',
        './app/**/*.{js,ts,jsx,tsx,mdx}',
    ],
    darkMode: 'class',
    theme: {
        extend: {
            colors: {
                // Premium dark theme palette
                dark: {
                    900: '#0a0a0f',
                    800: '#12121a',
                    700: '#1a1a26',
                    600: '#242436',
                    500: '#2e2e42',
                },
                accent: {
                    primary: '#6366f1', // Indigo
                    secondary: '#8b5cf6', // Violet
                    success: '#10b981', // Emerald
                    danger: '#ef4444', // Red
                    warning: '#f59e0b', // Amber
                },
                chart: {
                    up: '#22c55e',
                    down: '#ef4444',
                    line: '#6366f1',
                }
            },
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
                mono: ['JetBrains Mono', 'Menlo', 'monospace'],
            },
            animation: {
                'fade-in': 'fadeIn 0.3s ease-out',
                'slide-up': 'slideUp 0.4s ease-out',
                'pulse-glow': 'pulseGlow 2s infinite',
            },
            keyframes: {
                fadeIn: {
                    '0%': { opacity: '0' },
                    '100%': { opacity: '1' },
                },
                slideUp: {
                    '0%': { opacity: '0', transform: 'translateY(20px)' },
                    '100%': { opacity: '1', transform: 'translateY(0)' },
                },
                pulseGlow: {
                    '0%, 100%': { boxShadow: '0 0 20px rgba(99, 102, 241, 0.3)' },
                    '50%': { boxShadow: '0 0 40px rgba(99, 102, 241, 0.5)' },
                },
            },
        },
    },
    plugins: [],
};
