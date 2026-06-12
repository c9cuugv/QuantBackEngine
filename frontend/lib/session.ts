export function getSessionId(): string {
    if (typeof window === 'undefined') return '';
    const KEY = 'qbe_session';
    let id = localStorage.getItem(KEY);
    if (!id) { id = crypto.randomUUID(); localStorage.setItem(KEY, id); }
    return id;
}
