/** @type {import('next').NextConfig} */
const nextConfig = {
    reactStrictMode: true,
    output: 'standalone',
    async rewrites() {
        return [
            {
                source: '/api/:path*',
                destination: process.env.BACKEND_URL
                    ? `${process.env.BACKEND_URL}/api/:path*`
                    : 'http://localhost:8080/api/:path*',
            },
        ];
    },
};

module.exports = nextConfig;
