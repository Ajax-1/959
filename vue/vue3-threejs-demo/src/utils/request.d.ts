declare module './request.js' {
  export function get(url: string, params?: any, config?: any): Promise<any>;
  export function post(url: string, data?: any, config?: any): Promise<any>;
} 