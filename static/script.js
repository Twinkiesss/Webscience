document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('data-form');
    const xInput = document.getElementById('x-value');
    const ySelect = document.getElementById('y-value');
    const rHiddenInput = document.getElementById('r-value');
    const rButtons = document.querySelectorAll('.r-buttons button');
    const clearBtn = document.getElementById('clear-btn');
    const resultsTableBody = document.querySelector('#results-table tbody');
    const canvas = document.getElementById('plot');
    const ctx = canvas.getContext('2d');

    let currentR = 2;
    let history = [];

    const toastManager = new ToastManager();

    function loadHistoryFromStorage() {
        const savedHistory = localStorage.getItem('pointsHistory');
        if (savedHistory) {
            history = JSON.parse(savedHistory);
            updateResultsTable();
            drawPoints();
        }
    }

    function saveHistoryToStorage() {
        localStorage.setItem('pointsHistory', JSON.stringify(history));
    }

    loadHistoryFromStorage();

    drawPlot(currentR);

    rButtons.forEach(button => {
        button.addEventListener('click', function () {
            rButtons.forEach(btn => btn.classList.remove('active'));
            this.classList.add('active');
            currentR = parseFloat(this.dataset.r);
            rHiddenInput.value = currentR;
            drawPlot(currentR);
            drawPoints();
        });
    });

    form.addEventListener('submit', function (e) {
        e.preventDefault();

        let x = parseFloat(xInput.value);
        let y = parseFloat(ySelect.value);
        let r = currentR;

        if (isNaN(x) || x < -5 || x > 3) {
            showToast("Введите корректное значение X в диапазоне [-5;3]", 3000);
            return;
        }
        if (isNaN(y)) {
            showToast("Выберите значение Y", 3000);
            return;
        }

        const data = {X: x, Y: y, R: r};

        fetch('/fcgi-bin/app.jar', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data)
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Ошибка сервера: ' + response.status);
                }
                return response.json();
            })
            .then(data => {
                if (data.error) {
                    showWarning('Ошибка: ' + data.error, 5000);
                    return;
                }

                if (data.results && data.results.length > 0) {
                    let result = data.results[0];
                    let point = {
                        x: parseFloat(result.x),
                        y: parseFloat(result.y),
                        r: parseFloat(result.r),
                        hit: result.inArea,
                        current_time: result.currentTime,
                        execution_time: result.executionTime
                    };

                    history.push(point);
                    saveHistoryToStorage();
                    updateResultsTable();
                    drawPlot(currentR);
                    drawPoints();
                    if (point.hit) {
                        showSuccess(`Точка (${point.x}, ${point.y}) попала в область!`, 3000);
                    } else {
                        showWarning(`Точка (${point.x}, ${point.y}) не попала в область`, 3000);
                    }
                }
            })
            .catch(error => {
                console.error('Ошибка при отправке данных:', error);
                showWarning('Не удалось отправить данные на сервер: ' + error.message, 5000);
            });
    });

    clearBtn.addEventListener('click', function () {
        history = [];
        localStorage.removeItem('pointsHistory');
        updateResultsTable();
        drawPlot(currentR);
        showWarning("История результатов очищена", 3000);
    });

    xInput.addEventListener('input', function() {
        const value = this.value.replace(',', '.');
        const numValue = parseFloat(value);

        if (value && (isNaN(numValue) || numValue < -5 || numValue > 3)) {
            this.style.borderColor = 'rgba(26,40,51,0.3)';
            showToast("X должен быть числом от -5 до 3", 3000);
        } else {
            this.style.borderColor = '';
        }
    });

    function updateResultsTable() {
        resultsTableBody.innerHTML = '';
        [...history].reverse().forEach(item => {
            let row = document.createElement('tr');
            row.classList.add(item.hit ? 'hit-yes' : 'hit-no');
            row.innerHTML = `
                <td>${item.x}</td>
                <td>${item.y}</td>
                <td>${item.r}</td>
                <td>${item.hit ? 'Да' : 'Нет'}</td>
                <td>${item.current_time}</td>
                <td>${item.execution_time.toFixed(2)} ms</td>
            `;
            resultsTableBody.appendChild(row);
        });
    }

    function drawPlot(r) {
        let centerX = canvas.width / 2;
        let centerY = canvas.height / 2;
        let scale = 80; // px = 1 единица
        ctx.fillStyle = "black";
        ctx.strokeStyle = "black";
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        // arrow Y
        ctx.beginPath();
        ctx.moveTo(centerX - 5, 10);
        ctx.lineTo(centerX, 0);
        ctx.lineTo(centerX + 5, 10);
        ctx.stroke();
        ctx.closePath();

        // arrow X
        ctx.beginPath();
        ctx.moveTo(canvas.width - 10, centerY - 5);
        ctx.lineTo(canvas.width, centerY);
        ctx.lineTo(canvas.width - 10, centerY + 5);
        ctx.stroke();
        ctx.closePath();

        ctx.strokeStyle = "black";
        ctx.lineWidth = 2;

        // X
        ctx.beginPath();
        ctx.moveTo(0, centerY);
        ctx.lineTo(canvas.width, centerY);
        ctx.stroke();
        ctx.fillText("X", canvas.width - 15, centerY - 10);

        // Y
        ctx.beginPath();
        ctx.moveTo(centerX, 0);
        ctx.lineTo(centerX, canvas.height);
        ctx.stroke();
        ctx.fillText("Y", centerX + 10, 15);

        // -R
        let x1 = centerX - r * scale;
        ctx.moveTo(x1, centerY - 5);
        ctx.lineTo(x1, centerY + 5);
        ctx.stroke();
        ctx.fillText("-R", x1 - 10, centerY + 20);

        // -R/2
        let x2 = centerX - (r / 2) * scale;
        ctx.moveTo(x2, centerY - 5);
        ctx.lineTo(x2, centerY + 5);
        ctx.stroke();
        ctx.fillText("-R/2", x2 - 15, centerY + 20);

        // R/2
        let x3 = centerX + (r / 2) * scale;
        ctx.moveTo(x3, centerY - 5);
        ctx.lineTo(x3, centerY + 5);
        ctx.stroke();
        ctx.fillText("R/2", x3 - 10, centerY + 20);

        // R
        let x4 = centerX + r * scale;
        ctx.moveTo(x4, centerY - 5);
        ctx.lineTo(x4, centerY + 5);
        ctx.stroke();
        ctx.fillText("R", x4 - 5, centerY + 20);

        // -R
        let y1 = centerY + r * scale;
        ctx.moveTo(centerX - 5, y1);
        ctx.lineTo(centerX + 5, y1);
        ctx.stroke();
        ctx.fillText("-R", centerX + 10, y1 + 5);

        // -R/2
        let y2 = centerY + (r / 2) * scale;
        ctx.moveTo(centerX - 5, y2);
        ctx.lineTo(centerX + 5, y2);
        ctx.stroke();
        ctx.fillText("-R/2", centerX + 10, y2 + 5);

        // R/2
        let y3 = centerY - (r / 2) * scale;
        ctx.moveTo(centerX - 5, y3);
        ctx.lineTo(centerX + 5, y3);
        ctx.stroke();
        ctx.fillText("R/2", centerX + 10, y3 + 5);

        // R
        let y4 = centerY - r * scale;
        ctx.moveTo(centerX - 5, y4);
        ctx.lineTo(centerX + 5, y4);
        ctx.stroke();
        ctx.fillText("R", centerX + 10, y4 + 5);

        ctx.fillStyle = "rgba(52, 152, 219,0.4)";
        ctx.strokeStyle = "#2980b9";

        // quater of circle
        ctx.beginPath();
        ctx.moveTo(centerX, centerY);
        ctx.arc(centerX, centerY, r / 2 * scale, -Math.PI / 2, 0);
        ctx.closePath();
        ctx.fill();

        // rectangle
        ctx.fillRect(centerX, centerY, r * scale, r / 2 * scale);

        // triangle
        ctx.beginPath();
        ctx.moveTo(centerX, centerY);
        ctx.lineTo(centerX - r * scale / 2, centerY);
        ctx.lineTo(centerX, centerY + r * scale / 2);
        ctx.closePath();
        ctx.fill();
    }

    function drawPoints() {
        let centerX = canvas.width / 2;
        let centerY = canvas.height / 2;
        let scale = 80;

        history.forEach(item => {
            if (item.r === currentR) {
                ctx.beginPath();
                ctx.arc(centerX + item.x * scale, centerY - item.y * scale, 4, 0, 2 * Math.PI);
                ctx.fillStyle = item.hit ? "green" : "red";
                ctx.fill();
            }
        });
    }
});