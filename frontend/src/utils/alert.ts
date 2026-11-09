import Swal from 'sweetalert2'

/**
 * Mensajes con SweetAlert2 (reemplaza los avisos inline y los alert() nativos).
 */

export function alertError(message: string) {
  void Swal.fire({
    icon: 'error',
    title: 'Error',
    text: message,
    confirmButtonText: 'Aceptar',
    confirmButtonColor: '#be123c',
  })
}

export function alertSuccess(message: string) {
  void Swal.fire({
    icon: 'success',
    title: 'Éxito',
    text: message,
    confirmButtonText: 'Aceptar',
    confirmButtonColor: '#059669',
  })
}

export function alertInfo(message: string) {
  void Swal.fire({
    icon: 'info',
    title: 'Información',
    text: message,
    confirmButtonText: 'Aceptar',
    confirmButtonColor: '#e11d48',
  })
}

export async function confirmAction(message: string, confirmText = 'Aceptar'): Promise<boolean> {
  const result = await Swal.fire({
    icon: 'warning',
    title: 'Confirmar',
    text: message,
    showCancelButton: true,
    confirmButtonText: confirmText,
    cancelButtonText: 'Cancelar',
    confirmButtonColor: '#be123c',
    cancelButtonColor: '#64748b',
  })
  return result.isConfirmed
}
