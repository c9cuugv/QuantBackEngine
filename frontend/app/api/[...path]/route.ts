/**
 * Server-Side API Proxy
 *
 * Catches all /api/* requests from the browser and forwards them
 * to the Spring Boot backend inside the Docker network.
 */
import { NextRequest, NextResponse } from 'next/server';

const BACKEND = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || 'http://backend:8080';

async function proxy(req: NextRequest, { params }: { params: { path: string[] } }) {
    const path = params.path.join('/');
    const url = `${BACKEND}/api/${path}`;

    console.log(`[PROXY] ${req.method} /api/${path} -> ${url}`);

    const headers = new Headers();
    // Only forward safe headers
    const contentType = req.headers.get('content-type');
    if (contentType) headers.set('Content-Type', contentType);
    const accept = req.headers.get('accept');
    if (accept) headers.set('Accept', accept);

    const init: RequestInit = {
        method: req.method,
        headers,
    };

    // Forward body for non-GET requests
    if (req.method !== 'GET' && req.method !== 'HEAD') {
        // Read the body as text so we can log and forward it
        const bodyText = await req.text();
        console.log(`[PROXY] Body length: ${bodyText.length}`);
        init.body = bodyText;
    }

    try {
        const upstream = await fetch(url, init);
        const body = await upstream.arrayBuffer();

        console.log(`[PROXY] Upstream responded: ${upstream.status} ${upstream.statusText} (${body.byteLength} bytes)`);

        return new NextResponse(body, {
            status: upstream.status,
            statusText: upstream.statusText,
            headers: Object.fromEntries(
                Array.from(upstream.headers.entries()).filter(
                    ([k]) => !['transfer-encoding', 'content-encoding'].includes(k.toLowerCase())
                )
            ),
        });
    } catch (err) {
        console.error(`[PROXY] Fetch error:`, err);
        return NextResponse.json(
            { error: 'Backend unavailable', detail: String(err) },
            { status: 502 }
        );
    }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
