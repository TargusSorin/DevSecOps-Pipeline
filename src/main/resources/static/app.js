const TOKEN_KEY = "projectTrackerJwt";
let token = localStorage.getItem(TOKEN_KEY) || "";
let selectedProject = null;

const authSection = document.getElementById("authSection");
const appSection = document.getElementById("appSection");
const taskSection = document.getElementById("taskSection");
const taskSectionTitle = document.getElementById("taskSectionTitle");
const statusMessage = document.getElementById("statusMessage");
const projectList = document.getElementById("projectList");
const taskList = document.getElementById("taskList");

document.getElementById("registerForm").addEventListener("submit", onRegister);
document.getElementById("loginForm").addEventListener("submit", onLogin);
document.getElementById("logoutButton").addEventListener("click", onLogout);
document.getElementById("projectForm").addEventListener("submit", onCreateProject);
document.getElementById("taskForm").addEventListener("submit", onCreateTask);

updateAuthUi();
if (token) {
    loadProjects();
}

async function onRegister(event) {
    event.preventDefault();
    const formData = new FormData(event.target);
    const payload = {
        username: (formData.get("username") || "").toString().trim(),
        password: (formData.get("password") || "").toString()
    };

    try {
        const data = await api("/api/auth/register", { method: "POST", body: payload, requiresAuth: false });
        saveToken(data.token);
        event.target.reset();
        showStatus("Registered successfully.", false);
        await loadProjects();
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function onLogin(event) {
    event.preventDefault();
    const formData = new FormData(event.target);
    const payload = {
        username: (formData.get("username") || "").toString().trim(),
        password: (formData.get("password") || "").toString()
    };

    try {
        const data = await api("/api/auth/login", { method: "POST", body: payload, requiresAuth: false });
        saveToken(data.token);
        event.target.reset();
        showStatus("Logged in.", false);
        await loadProjects();
    } catch (error) {
        showStatus(error.message, true);
    }
}

function onLogout() {
    token = "";
    selectedProject = null;
    localStorage.removeItem(TOKEN_KEY);
    projectList.innerHTML = "";
    taskList.innerHTML = "";
    taskSection.classList.add("hidden");
    updateAuthUi();
    showStatus("Logged out.", false);
}

async function onCreateProject(event) {
    event.preventDefault();
    const formData = new FormData(event.target);
    const payload = {
        name: (formData.get("name") || "").toString().trim(),
        description: (formData.get("description") || "").toString().trim()
    };

    try {
        await api("/api/projects", { method: "POST", body: payload });
        event.target.reset();
        showStatus("Project created.", false);
        await loadProjects();
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function onCreateTask(event) {
    event.preventDefault();
    if (!selectedProject) {
        showStatus("Select a project first.", true);
        return;
    }

    const formData = new FormData(event.target);
    const dueDate = (formData.get("dueDate") || "").toString();
    const payload = {
        title: (formData.get("title") || "").toString().trim(),
        description: (formData.get("description") || "").toString().trim(),
        status: (formData.get("status") || "TODO").toString(),
        dueDate: dueDate || null
    };

    try {
        await api(`/api/projects/${selectedProject.id}/tasks`, { method: "POST", body: payload });
        event.target.reset();
        showStatus("Task created.", false);
        await loadTasks(selectedProject);
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function loadProjects() {
    try {
        const projects = await api("/api/projects");
        renderProjects(projects);
        updateAuthUi();
    } catch (error) {
        handleAuthError(error);
    }
}

async function loadTasks(project) {
    try {
        const tasks = await api(`/api/projects/${project.id}/tasks`);
        selectedProject = project;
        taskSection.classList.remove("hidden");
        taskSectionTitle.textContent = `Tasks for "${project.name}"`;
        renderTasks(tasks, project);
    } catch (error) {
        showStatus(error.message, true);
    }
}

function renderProjects(projects) {
    projectList.innerHTML = "";
    if (projects.length === 0) {
        projectList.innerHTML = "<li class='list-item'>No projects yet.</li>";
        return;
    }

    projects.forEach((project) => {
        const item = document.createElement("li");
        item.className = "list-item";
        item.innerHTML = `
            <h5>${escapeHtml(project.name)}</h5>
            <p>${escapeHtml(project.description || "")}</p>
            <div class="row-actions">
                <button data-action="open">Open Tasks</button>
                <button data-action="edit">Edit</button>
                <button data-action="delete" class="secondary">Delete</button>
            </div>
        `;

        const [openButton, editButton, deleteButton] = item.querySelectorAll("button");
        openButton.addEventListener("click", () => loadTasks(project));
        editButton.addEventListener("click", () => editProject(project));
        deleteButton.addEventListener("click", () => deleteProject(project));

        projectList.appendChild(item);
    });
}

function renderTasks(tasks, project) {
    taskList.innerHTML = "";
    if (tasks.length === 0) {
        taskList.innerHTML = "<li class='list-item'>No tasks yet.</li>";
        return;
    }

    tasks.forEach((task) => {
        const item = document.createElement("li");
        item.className = "list-item";
        item.innerHTML = `
            <h5>${escapeHtml(task.title)}</h5>
            <p>${escapeHtml(task.description || "")}</p>
            <p>Status: <strong>${escapeHtml(task.status)}</strong></p>
            <p>Due: ${escapeHtml(task.dueDate || "-")}</p>
            <div class="row-actions">
                <button data-action="edit">Edit</button>
                <button data-action="delete" class="secondary">Delete</button>
            </div>
        `;

        const [editButton, deleteButton] = item.querySelectorAll("button");
        editButton.addEventListener("click", () => editTask(project, task));
        deleteButton.addEventListener("click", () => deleteTask(project, task));

        taskList.appendChild(item);
    });
}

async function editProject(project) {
    const name = prompt("Project name:", project.name);
    if (name === null) {
        return;
    }
    const description = prompt("Project description:", project.description || "");
    if (description === null) {
        return;
    }

    try {
        await api(`/api/projects/${project.id}`, {
            method: "PUT",
            body: { name: name.trim(), description: description.trim() }
        });
        showStatus("Project updated.", false);
        await loadProjects();
        if (selectedProject && selectedProject.id === project.id) {
            await loadTasks({ ...project, name: name.trim() });
        }
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function deleteProject(project) {
    if (!confirm(`Delete project "${project.name}"?`)) {
        return;
    }

    try {
        await api(`/api/projects/${project.id}`, { method: "DELETE" });
        if (selectedProject && selectedProject.id === project.id) {
            selectedProject = null;
            taskSection.classList.add("hidden");
            taskList.innerHTML = "";
        }
        showStatus("Project deleted.", false);
        await loadProjects();
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function editTask(project, task) {
    const title = prompt("Task title:", task.title);
    if (title === null) {
        return;
    }
    const description = prompt("Task description:", task.description || "");
    if (description === null) {
        return;
    }
    const status = prompt("Task status (TODO, IN_PROGRESS, DONE):", task.status);
    if (status === null) {
        return;
    }
    const dueDate = prompt("Due date (YYYY-MM-DD, optional):", task.dueDate || "");
    if (dueDate === null) {
        return;
    }

    try {
        await api(`/api/projects/${project.id}/tasks/${task.id}`, {
            method: "PUT",
            body: {
                title: title.trim(),
                description: description.trim(),
                status: status.trim() || "TODO",
                dueDate: dueDate.trim() || null
            }
        });
        showStatus("Task updated.", false);
        await loadTasks(project);
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function deleteTask(project, task) {
    if (!confirm(`Delete task "${task.title}"?`)) {
        return;
    }

    try {
        await api(`/api/projects/${project.id}/tasks/${task.id}`, { method: "DELETE" });
        showStatus("Task deleted.", false);
        await loadTasks(project);
    } catch (error) {
        showStatus(error.message, true);
    }
}

async function api(path, options = {}) {
    const method = options.method || "GET";
    const requiresAuth = options.requiresAuth !== false;
    const headers = { "Content-Type": "application/json" };
    if (requiresAuth && token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(path, {
        method,
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined
    });

    const text = await response.text();
    const data = text ? tryParseJson(text) : null;

    if (!response.ok) {
        const message = extractErrorMessage(data, response.status);
        throw new Error(message);
    }
    return data;
}

function extractErrorMessage(data, statusCode) {
    if (!data) {
        return `Request failed (${statusCode})`;
    }
    if (typeof data === "string") {
        return data;
    }
    return data.message || data.error || `Request failed (${statusCode})`;
}

function tryParseJson(text) {
    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

function saveToken(newToken) {
    token = newToken;
    localStorage.setItem(TOKEN_KEY, token);
    updateAuthUi();
}

function updateAuthUi() {
    const authenticated = Boolean(token);
    authSection.classList.toggle("hidden", authenticated);
    appSection.classList.toggle("hidden", !authenticated);
}

function handleAuthError(error) {
    if (error.message.includes("401") || error.message.toLowerCase().includes("unauthorized")) {
        onLogout();
        showStatus("Session expired. Please login again.", true);
        return;
    }
    showStatus(error.message, true);
}

function showStatus(message, isError) {
    statusMessage.textContent = message || "";
    statusMessage.classList.remove("error", "success");
    statusMessage.classList.add(isError ? "error" : "success");
}

function escapeHtml(value) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}
