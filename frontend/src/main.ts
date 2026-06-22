import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import { setTokenProvider } from './api/http';
import { setAgentTokenProvider } from './api/agent';
import { setDocumentsTokenProvider } from './api/documents';
import { getAccessToken } from './composables/authState';
import './style.css';
import './styles/animations.css';
import './styles/transitions.css';
import { vAnimate } from './directives/animate';

setTokenProvider(getAccessToken);
setAgentTokenProvider(getAccessToken);
setDocumentsTokenProvider(getAccessToken);

createApp(App).use(ElementPlus).use(router).directive('animate', vAnimate).mount('#app');