import type { DirectiveBinding, ObjectDirective } from 'vue';

export type AnimateType =
  | 'fade-in'
  | 'slide-up'
  | 'slide-down'
  | 'slide-in-left'
  | 'slide-in-right'
  | 'scale-in'
  | 'zoom-in';

export type AnimateTrigger = 'scroll' | 'load' | 'click';

export interface AnimateOptions {
  type?: AnimateType;
  duration?: string;
  delay?: string;
  easing?: string;
  trigger?: AnimateTrigger;
  threshold?: number;
  rootMargin?: string;
  once?: boolean;
}

const DEFAULT_OPTIONS: Required<AnimateOptions> = {
  type: 'fade-in',
  duration: '0.6s',
  delay: '0s',
  easing: 'cubic-bezier(0.22, 0.61, 0.36, 1)',
  trigger: 'scroll',
  threshold: 0.1,
  rootMargin: '0px 0px -60px 0px',
  once: true,
};

function normalizeOptions(value: AnimateOptions | AnimateType | undefined): Required<AnimateOptions> {
  if (!value) return { ...DEFAULT_OPTIONS };
  if (typeof value === 'string') {
    return { ...DEFAULT_OPTIONS, type: value };
  }
  return { ...DEFAULT_OPTIONS, ...value };
}

function applyCssVariables(el: HTMLElement, options: Required<AnimateOptions>) {
  el.style.setProperty('--animate-duration', options.duration);
  el.style.setProperty('--animate-delay', options.delay);
  el.style.setProperty('--animate-easing', options.easing);
}

function handleScrollTrigger(
  el: HTMLElement,
  options: Required<AnimateOptions>
): IntersectionObserver | undefined {
  if (!window.IntersectionObserver) {
    el.classList.add('is-visible');
    return undefined;
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          el.classList.add('is-visible');
          if (options.once) {
            observer.unobserve(el);
          }
        } else if (!options.once) {
          el.classList.remove('is-visible');
        }
      });
    },
    {
      threshold: options.threshold,
      rootMargin: options.rootMargin,
    }
  );

  observer.observe(el);
  return observer;
}

function handleLoadTrigger(el: HTMLElement) {
  requestAnimationFrame(() => {
    el.classList.add('is-visible');
  });
}

function handleClickTrigger(el: HTMLElement) {
  el.addEventListener('click', () => {
    el.classList.remove('is-visible');
    void el.offsetWidth; // force reflow to restart transition
    el.classList.add('is-visible');
  });
}

interface AnimateElement extends HTMLElement {
  __animateObserver?: IntersectionObserver;
  __animateClickHandler?: () => void;
}

export const vAnimate: ObjectDirective<AnimateElement, AnimateOptions | AnimateType | undefined> = {
  mounted(el, binding) {
    const options = normalizeOptions(binding.value);

    el.classList.add('animate', options.type);
    applyCssVariables(el, options);

    if (options.trigger === 'load') {
      handleLoadTrigger(el);
    } else if (options.trigger === 'click') {
      handleClickTrigger(el);
    } else {
      el.__animateObserver = handleScrollTrigger(el, options);
    }
  },
  updated(el, binding) {
    const options = normalizeOptions(binding.value);
    applyCssVariables(el, options);
  },
  unmounted(el) {
    if (el.__animateObserver) {
      el.__animateObserver.disconnect();
      delete el.__animateObserver;
    }
    if (el.__animateClickHandler) {
      el.removeEventListener('click', el.__animateClickHandler);
      delete el.__animateClickHandler;
    }
  },
};
