const message = document.getElementById("feedbackMessage");
const username = document.getElementById("username");
const password = document.getElementById("password");
const confirmPassword = document.getElementById("confirmPassword");
const registrationForm = document.getElementById("loginform");
const pending = new Map();
let nextCallId = 0;

message.hidden = true;

function callNative(method, ...args) {
    return new Promise(resolve => {
        const callId = String(++nextCallId);
        pending.set(callId, resolve);
        Android[method](callId, ...args);
    });
}

window.onNativeResult = (callId, result) => {
    pending.get(String(callId))?.(result);
    pending.delete(String(callId));
};

registrationForm.addEventListener("submit", async event => {
    event.preventDefault();
    message.hidden = false;

    if (password.value !== confirmPassword.value) {
        message.style.color = "red";
        message.textContent = "A két jelszó nem egyezik.";
        return;
    }

    const created = await callNative("register", username.value, password.value);
    message.style.color = created ? "green" : "red";
    message.textContent = created
        ? "Sikeres regisztráció. Most bejelentkezhetsz."
        : "Ez a felhasználónév már foglalt.";
    if (created) location.replace("../login-page/login.html");
});