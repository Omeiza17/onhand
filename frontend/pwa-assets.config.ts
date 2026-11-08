import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

// Generates the PWA icons from public/icon.svg: `npm run generate-pwa-assets`.
// Maskable and Apple icons are padded, so fill the padding with the accent colour.
const accent = { background: '#0E6B5E' }

export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...minimal2023Preset,
    maskable: { ...minimal2023Preset.maskable, resizeOptions: accent },
    apple: { ...minimal2023Preset.apple, resizeOptions: accent },
  },
  images: ['public/icon.svg'],
})
