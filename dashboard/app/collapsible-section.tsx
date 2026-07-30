'use client';

import { Disclosure, Transition } from '@headlessui/react';
import { ChevronDownIcon } from '@heroicons/react/24/outline';
import { Fragment } from 'react';

export default function CollapsibleSection({
  title,
  subtitle,
  defaultOpen = false,
  headerRight,
  children
}: {
  title: string;
  subtitle?: string;
  defaultOpen?: boolean;
  headerRight?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Disclosure defaultOpen={defaultOpen}>
      {({ open }) => (
        <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
          <Disclosure.Button className="flex w-full items-center justify-between px-4 py-3 text-left">
            <div>
              <p className="font-medium text-gray-900">{title}</p>
              {subtitle && (
                <p className="text-sm text-gray-500">{subtitle}</p>
              )}
            </div>
            <div className="flex items-center gap-3">
              {headerRight}
              <ChevronDownIcon
                className={
                  'h-5 w-5 flex-shrink-0 text-gray-400 transition-transform ' +
                  (open ? 'rotate-180' : '')
                }
              />
            </div>
          </Disclosure.Button>
          <Transition
            as={Fragment}
            enter="transition duration-100 ease-out"
            enterFrom="transform scale-95 opacity-0"
            enterTo="transform scale-100 opacity-100"
            leave="transition duration-75 ease-out"
            leaveFrom="transform scale-100 opacity-100"
            leaveTo="transform scale-95 opacity-0"
          >
            <Disclosure.Panel className="border-t border-gray-100 px-4 py-4">
              {children}
            </Disclosure.Panel>
          </Transition>
        </div>
      )}
    </Disclosure>
  );
}
