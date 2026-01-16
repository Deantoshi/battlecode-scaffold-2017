declare module "*.txt" {
  const content: string
  export default content
}

declare module "path" {
  export function isAbsolute(path: string): boolean
  export function join(...paths: string[]): string
}

declare module "fs/promises" {
  export function writeFile(path: string, data: string, options?: string): Promise<void>
}

declare const process: {
  cwd: () => string
}
