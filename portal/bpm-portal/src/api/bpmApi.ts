import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080', // ou sua URL real
})

export const getProcesses = () => api.get('/processes')
export const startProcess = (id: number) => api.post(`/processes/${id}/start`)
export const getTasks = () => api.get('/tasks')
export const getTaskById = (id: number) => api.get(`/tasks/${id}`)
export const completeTask = (id: number, payload: any) => api.post(`/tasks/${id}/complete`, payload)
export const getFormById = (id: number) => api.get(`/forms/${id}`)

export default api