declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
 
declare module '*.js' {
  const content: any
  export default content
  export * from '*.js'
} 