// Archivo de ejemplo: siempre usar rutas relativas que comiencen con /api/
export async function fetchHealth() {
  const res = await fetch('/api/health')
  if (!res.ok) throw new Error('Network response was not ok')
  return res.json()
}

