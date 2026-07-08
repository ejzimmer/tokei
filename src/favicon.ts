const SIZE = 64
const BG_COLOR = "#1a1a2e"
const FACE_COLOR = "#f5f5f0"
const TICK_COLOR = "#1a1a2e"
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

// Mirrors the look of the static app icon (navy square, off-white face,
// hour ticks) minus its two fixed hands, so the live progress hand is the
// only thing that moves.
function drawBase(c: CanvasRenderingContext2D) {
  const mid = SIZE / 2
  const faceR = SIZE * 0.4

  c.clearRect(0, 0, SIZE, SIZE)
  c.fillStyle = BG_COLOR
  c.fillRect(0, 0, SIZE, SIZE)

  c.beginPath()
  c.arc(mid, mid, faceR, 0, Math.PI * 2)
  c.fillStyle = FACE_COLOR
  c.fill()

  for (let i = 0; i < 12; i++) {
    const angle = (i / 12) * Math.PI * 2
    const isMajor = i % 3 === 0
    const rOuter = faceR * 0.92
    const rInner = faceR * (isMajor ? 0.78 : 0.84)
    c.beginPath()
    c.moveTo(mid + rInner * Math.sin(angle), mid - rInner * Math.cos(angle))
    c.lineTo(mid + rOuter * Math.sin(angle), mid - rOuter * Math.cos(angle))
    c.lineWidth = isMajor ? SIZE * 0.03 : SIZE * 0.018
    c.strokeStyle = TICK_COLOR
    c.lineCap = "round"
    c.stroke()
  }

  return { mid, faceR }
}

/** progress: 0-1 fraction through the run, or null to restore the static icon. */
export function setFaviconProgress(progress: number | null) {
  ensureLink()
  if (progress === null) {
    if (defaultHref) setHref(defaultHref)
    return
  }

  const c = getCanvasContext()
  const { mid, faceR } = drawBase(c)

  const angle = Math.max(0, Math.min(1, progress)) * Math.PI * 2 - Math.PI / 2
  c.beginPath()
  c.moveTo(mid, mid)
  c.lineTo(mid + Math.cos(angle) * faceR * 0.78, mid + Math.sin(angle) * faceR * 0.78)
  c.lineWidth = SIZE * 0.08
  c.lineCap = "round"
  c.strokeStyle = HAND_COLOR
  c.stroke()

  c.beginPath()
  c.arc(mid, mid, SIZE * 0.07, 0, Math.PI * 2)
  c.fillStyle = HAND_COLOR
  c.fill()

  setHref(canvas!.toDataURL("image/png"))
}
