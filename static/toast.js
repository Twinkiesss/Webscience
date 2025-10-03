class ToastManager {
    constructor() {
        this.toastContainer = this.createContainer();
        this.toasts = new Set();
    }

    createContainer() {
        const container = document.createElement('div');
        container.className = 'toast-container';
        container.style.cssText = `
          position: fixed;
          top: 20px;
          right: 20px;
          z-index: 1000;
          display: flex;
          flex-direction: column;
          gap: 10px;
        `;
        document.body.appendChild(container);
        return container;
    }

    show(message, options = {}) {
        const {
            type = 'info',
            duration = 3000,
            animation = 'slide',
            position = 'top-right'
        } = options;

        const toast = this.createToast(message, type, animation);
        this.toastContainer.appendChild(toast);
        this.toasts.add(toast);

        setTimeout(() => {
            toast.classList.add('show');
        }, 10);

        if (duration > 0) {
            setTimeout(() => {
                this.hide(toast);
            }, duration);
        }

        return toast;
    }

    createToast(message, type, animation) {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `
          <div class="toast-content">
            <span class="toast-message">${message}</span>
            <button class="toast-close">×</button>
          </div>
        `;

        toast.querySelector('.toast-close').addEventListener('click', function() {
            toastManager.hide(toast);
        });

        return toast;
    }

    hide(toast) {
        if (!toast) return;

        toast.classList.add('hiding');

        toast.addEventListener('animationend', () => {
            if (toast.parentElement) {
                toast.parentElement.removeChild(toast);
            }
            this.toasts.delete(toast);
        });
    }

    hideAll() {
        this.toasts.forEach(toast => this.hide(toast));
    }
}

const toastManager = new ToastManager();

function showToast(message, type = 'info', duration = 3000) {
    return toastManager.show(message, { type, duration });
}

function showSuccess(message, duration = 3000) {
    return toastManager.show(message, { type: 'success', duration });
}

function showError(message, duration = 5000) {
    return toastManager.show(message, { type: 'error', duration });
}

function showWarning(message, duration = 4000) {
    return toastManager.show(message, { type: 'warning', duration });
}