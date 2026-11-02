(() => {
    const dialog = document.querySelector('#appointment-dialog');
    if (dialog && typeof dialog.showModal === 'function') {
        const content = dialog.querySelector('.dialog-content');
        document.querySelectorAll('.view-button').forEach((button) => {
            button.hidden = false;
            button.addEventListener('click', () => {
                const details = button.closest('td').querySelector('.appointment-details');
                content.replaceChildren(details.content.cloneNode(true));
                dialog.showModal();
            });
        });
        dialog.addEventListener('click', (event) => {
            if (event.target === dialog) {
                const rect = dialog.getBoundingClientRect();
                if (event.clientX < rect.left || event.clientX > rect.right ||
                    event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
            }
        });
    }
    document.querySelectorAll('.delete-form').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm('Удалить запись на приём? Это действие нельзя отменить.')) {
                event.preventDefault();
            }
        });
    });
})();
