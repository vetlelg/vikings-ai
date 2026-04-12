const s = { width: 14, height: 14, verticalAlign: 'middle' as const, marginRight: 4 };

export function TimberIcon() {
  return <svg viewBox="0 0 16 16" style={s}><rect x="3" y="2" width="4" height="12" rx="1" fill="#8b6914"/><rect x="9" y="4" width="4" height="10" rx="1" fill="#6b4c2a"/></svg>;
}
export function FishIcon() {
  return <svg viewBox="0 0 16 16" style={s}><ellipse cx="8" cy="8" rx="5" ry="3" fill="#3498db"/><path d="M13 8L16 5V11L13 8Z" fill="#3498db"/></svg>;
}
export function IronIcon() {
  return <svg viewBox="0 0 16 16" style={s}><polygon points="8,2 14,8 8,14 2,8" fill="#7f8c8d"/></svg>;
}
export function FursIcon() {
  return <svg viewBox="0 0 16 16" style={s}><ellipse cx="8" cy="8" rx="6" ry="5" fill="#8b5e3c"/><ellipse cx="8" cy="7" rx="4" ry="3" fill="#a0724a"/></svg>;
}
