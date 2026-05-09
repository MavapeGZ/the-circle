export async function fetchHealth() {
  const res = await fetch('/api/health')
  if (!res.ok) throw new Error('Network response was not ok')
  return res.json()
}

