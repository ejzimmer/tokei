const SIZE = 64
const FACE_COLOR = "#f5f5f0"
const RIM_COLOR = "#1a1a2e"
const HAND_COLOR = "#ff6b35"

let canvas: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let currentLink: HTMLLinkElement | null = null
let defaultHref = ""

function getCanvasContext(): CanvasRenderingContext2D {
  if (!canvas) {
    canvas = document.createElement("canvas")
    canvas.width = SIZE
    canvas.height = SIZE
    ctx = canvas.getContext("2d")
  }
  return ctx!
}

function ensureLink(): HTMLLinkElement | null {
  if (!currentLink) {
    currentLink = document.querySelector('link[rel="icon"]')
    if (currentLink) defaultHref = currentLink.href
  }
  return currentLink
}

// Some browsers only repaint the tab icon when the <link> node itself
// changes, not just its href — swap in a fresh node each update.
function setHref(href: string) {
  const old = ensureLink()
  if (!old) return
  const next = document.createElement("link")
  next.rel = "icon"
  next.type = "image/png"
  next.href = href
  old.replaceWith(next)
  currentLink = next
}

/** progress: 0-1 fraction through the run, or null to restore the static icon. */
export function setFaviconProgress(progress: number | null) {
  ensureLink()
  if (progress === null) {
    if (defaultHref) setHref(defaultHref)
    return
  }

  const c = getCanvasContext()
  const mid = SIZE / 2
  c.clearRect(0, 0, SIZE, SIZE)

  c.beginPath()
  c.arc(mid, mid, SIZE * 0.46, 0, Math.PI * 2)
  c.fillStyle = FACE_COLOR
  c.fill()
  c.lineWidth = SIZE * 0.07
  c.strokeStyle = RIM_COLOR
  c.stroke()

  const angle = Math.max(0, Math.min(1, progress)) * Math.PI * 2 - Math.PI / 2
  c.beginPath()
  c.moveTo(mid, mid)
  c.lineTo(mid + Math.cos(angle) * SIZE * 0.34, mid + Math.sin(angle) * SIZE * 0.34)
  c.lineWidth = SIZE * 0.1
  c.lineCap = "round"
  c.strokeStyle = HAND_COLOR
  c.stroke()

  c.beginPath()
  c.arc(mid, mid, SIZE * 0.07, 0, Math.PI * 2)
  c.fillStyle = HAND_COLOR
  c.fill()

  setHref(canvas!.toDataURL("image/png"))
}
